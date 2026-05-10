package com.beto.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.beto.app.BetoApplication
import com.beto.app.MainActivity
import com.beto.app.R
import com.beto.app.action.ActionDispatcher
import com.beto.app.action.AgentBusVoiceCapture
import com.beto.app.action.ChannelClarifier
import com.beto.app.action.ContactClarifier
import com.beto.app.action.TtsSpeaker
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentCommand
import com.beto.app.bus.AgentEvent
import com.beto.app.companion.CompanionActivity
import com.beto.app.companion.CompanionLlmClient
import com.beto.app.contacts.ContactRepository
import com.beto.app.guide.GuideController
import com.beto.app.llm.AnthropicClientHolder
import com.beto.app.llm.ClaudeLlmClient
import com.beto.app.overlay.OverlayManager
import com.beto.app.overlay.ScamOverlayManager
import com.beto.app.scam.ScamExplainer
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import com.beto.app.voice.VoiceCaptureActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

class BetoForegroundService : LifecycleService() {

    private var bootGreetingPlayed = false
    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var guideController: GuideController
    // El Explainer usa Sonnet 4.6 (mejor redacción warm) — async con timeout 1.5s y
    // fallback canned, así que la latencia extra no impacta el path crítico de comandos.
    private val scamExplainer by lazy {
        ScamExplainer(
            llm = ClaudeLlmClient(
                generateContent = { prompt ->
                    AnthropicClientHolder.complete(
                        prompt = prompt,
                        maxTokens = 256,
                        model = AnthropicClientHolder.QUALITY_MODEL,
                    )
                },
            ),
        )
    }
    private val clarificationCaptureActive = AtomicBoolean(false)
    private val handledClarificationTranscripts = Collections.synchronizedSet(mutableSetOf<String>())
    private val guideActive = AtomicBoolean(false)
    private val companionActive = AtomicBoolean(false)

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(LogTags.TTS).i("BetoForegroundService.onCreate")
        BetoApplication.ensureNotificationChannel(this)
        startForegroundCorrectly()
        val speaker = TtsSpeaker()
        val contacts = ContactRepository(this)
        val voiceCapture = AgentBusVoiceCapture(
            onCaptureStarted = { clarificationCaptureActive.set(true) },
            onCaptured = { handledClarificationTranscripts.add(it) },
            onCaptureFinished = { clarificationCaptureActive.set(false) },
        )
        val contactClarifier = ContactClarifier(
            speaker = speaker,
            contacts = contacts,
            memory = BetoApplication.userMemoryStore,
            voiceCapture = voiceCapture,
        )
        val channelClarifier = ChannelClarifier(
            speaker = speaker,
            voiceCapture = voiceCapture,
            memory = BetoApplication.userMemoryStore,
        )
        guideController = GuideController(context = applicationContext)
        actionDispatcher = ActionDispatcher(
            context = this,
            llm = ClaudeLlmClient(),
            memory = BetoApplication.userMemoryStore,
            contacts = contacts,
            contactClarifier = contactClarifier,
            channelClarifier = channelClarifier,
            speaker = speaker,
            phraseGenerator = BetoApplication.phraseGenerator,
            guideController = guideController,
            companionChat = CompanionLlmClient(),
            scope = lifecycleScope,
        )

        lifecycleScope.launch {
            AgentBus.commands.collect { command ->
                when (command) {
                    is AgentCommand.Speak -> {
                        Timber.tag(LogTags.TTS).d("Cmd Speak -> %s", command.text)
                        TtsManager.speak(command.text)
                    }
                    is AgentCommand.StartVoiceCapture -> {
                        when {
                            // User-initiated y TTS activo → interrumpir y abrir mic con grace mínimo
                            // para que el speaker termine de drenar el último sample.
                            command.interruptTts && TtsManager.isBusySpeaking() -> {
                                Timber.tag(LogTags.STT).i(
                                    "StartVoiceCapture user-interrupt -> stopping TTS & opening mic",
                                )
                                TtsManager.stopAll()
                                launch {
                                    delay(INTERRUPT_GRACE_MS)
                                    startActivity(
                                        VoiceCaptureActivity.startIntent(
                                            this@BetoForegroundService,
                                            command.startedAtMs,
                                        ),
                                    )
                                }
                            }
                            // Auto-initiated (clarifier) y TTS activo → diferir hasta que termine.
                            // No-interrupt path: esperamos hasta drain completo + grace.
                            TtsManager.isBusySpeaking() -> {
                                Timber.tag(LogTags.STT).i(
                                    "StartVoiceCapture deferred — TTS busy (anti-loop)",
                                )
                                launch {
                                    val deadline = System.currentTimeMillis() + 8_000L
                                    while (
                                        TtsManager.isBusySpeaking() &&
                                        System.currentTimeMillis() < deadline
                                    ) {
                                        delay(80L)
                                    }
                                    delay(GRACE_AFTER_TTS_MS)
                                    startActivity(
                                        VoiceCaptureActivity.startIntent(
                                            this@BetoForegroundService,
                                            command.startedAtMs,
                                        ),
                                    )
                                }
                            }
                            else -> {
                                startActivity(
                                    VoiceCaptureActivity.startIntent(
                                        this@BetoForegroundService,
                                        command.startedAtMs,
                                    ),
                                )
                            }
                        }
                    }
                    AgentCommand.OpenCompanion -> {
                        val intent = Intent(this@BetoForegroundService, CompanionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    }
                    AgentCommand.StopVoiceCapture -> {
                        // Lo consume VoiceCaptureActivity directamente vía bus collector.
                        // No-op acá — solo lo dejamos pasar por el bus.
                    }
                }
            }
        }

        lifecycleScope.launch {
            AgentBus.events.collect { event ->
                when (event) {
                    is AgentEvent.BubbleTapped -> {
                        if (guideActive.get()) {
                            Timber.tag(LogTags.ACCESSIBILITY).i("Bubble tapped during guide -> cancel guide")
                            guideController.cancel()
                        } else {
                            Timber.tag(LogTags.STT).i("Bubble tapped -> voice capture")
                            AgentBus.command(
                                AgentCommand.StartVoiceCapture(
                                    startedAtMs = event.startedAtMs,
                                    interruptTts = true,
                                ),
                            )
                        }
                    }
                    is AgentEvent.GuideStarted -> guideActive.set(true)
                    is AgentEvent.GuideEnded -> guideActive.set(false)
                    is AgentEvent.GuideCancelled -> guideActive.set(false)
                    is AgentEvent.VoiceCaptured -> {
                        if (
                            clarificationCaptureActive.get() ||
                            handledClarificationTranscripts.remove(event.text) ||
                            companionActive.get()
                        ) {
                            // Si el chat está abierto, el chat captura el VoiceCaptured y lo
                            // re-emite como ChatMessageSent (mismo dispatcher al final).
                            return@collect
                        }
                        // Beto unificado: misma ruta que el chat. Si LLM no detecta tool,
                        // el dispatcher cae a respuesta conversacional warm.
                        actionDispatcher.handle(event.text)
                    }
                    is AgentEvent.ChatMessageSent -> {
                        Timber.tag(LogTags.ACTION).d("CHAT_MESSAGE_DISPATCH text=%s", event.text)
                        actionDispatcher.handle(event.text)
                    }
                    is AgentEvent.VoiceCaptureFailed -> {
                        TtsManager.speak("No te escuché bien. Probemos de nuevo, dale.")
                    }
                    is AgentEvent.BubbleLongPressed -> {
                        Timber.tag(LogTags.TTS).i("Bubble long-pressed -> opening Companion")
                        AgentBus.command(AgentCommand.OpenCompanion)
                    }
                    is AgentEvent.BubbleCloseRequested -> {
                        Timber.tag(LogTags.TTS).i("Bubble close requested -> stopping Beto")
                        stopSelf()
                    }
                    is AgentEvent.ScamRiskDetected -> {
                        Timber.tag(LogTags.ACCESSIBILITY).w(
                            "ScamRiskDetected pkg=%s level=%s signals=%s",
                            event.packageName,
                            event.assessment.level,
                            event.assessment.signals.map { it.name },
                        )
                        // Mostramos overlay YA con la frase canned (offline-first del pitch).
                        ScamOverlayManager.show(
                            context = this@BetoForegroundService,
                            assessment = event.assessment,
                            contextHash = event.contextHash,
                        )
                        // En paralelo: pedimos al LLM Explainer una frase warm contextual.
                        // Si llega antes de que el user descarte el overlay, swap. Si no, no-op
                        // (updateBody chequea hash). El motor local SIGUE siendo el que decidió.
                        launch {
                            val trustedDesc = runCatching {
                                BetoApplication.trustedContactsRepository.current()?.voiceLabel
                            }.getOrNull()
                            val warm = scamExplainer.explain(
                                assessment = event.assessment,
                                rawText = event.text,
                                trustedContactDescription = trustedDesc,
                            )
                            if (!warm.isNullOrBlank()) {
                                ScamOverlayManager.updateBody(event.contextHash, warm)
                                // Hablar la frase warm con TTS — sin esperar (user puede haber
                                // tocado un botón ya). interrumpe cualquier TTS previo.
                                TtsManager.stopAll()
                                TtsManager.speak(warm)
                            }
                        }
                    }
                    AgentEvent.CompanionOpened -> {
                        Timber.tag(LogTags.ACCESSIBILITY).i("Companion opened -> hiding bubble")
                        companionActive.set(true)
                        OverlayManager.hide(this@BetoForegroundService)
                    }
                    AgentEvent.CompanionClosed -> {
                        Timber.tag(LogTags.ACCESSIBILITY).i("Companion closed -> re-showing bubble")
                        companionActive.set(false)
                        OverlayManager.show(this@BetoForegroundService)
                    }
                    else -> Unit
                }
            }
        }

        lifecycleScope.launch { AgentBus.emit(AgentEvent.ServiceStarted) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.tag(LogTags.TTS).i("onStartCommand startId=%d", startId)

        if (intent?.action == ACTION_STOP) {
            Timber.tag(LogTags.TTS).i("Stop action received -> stopping Beto")
            stopSelf()
            return START_NOT_STICKY
        }

        OverlayManager.show(this)

        if (!bootGreetingPlayed) {
            bootGreetingPlayed = true
            Timber.tag(LogTags.TTS).i("Disparando speakBootGreeting (D-10)")
            TtsManager.speakBootGreeting()
            lifecycleScope.launch { AgentBus.emit(AgentEvent.BootCompleted) }
        }

        return START_STICKY
    }

    private fun startForegroundCorrectly() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Timber.tag(LogTags.TTS).i("startForeground OK type=microphone")
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, flags)
        val stopIntent = Intent(this, BetoForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, flags)

        return NotificationCompat.Builder(this, BetoApplication.FGS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_beto_notification)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(R.string.fgs_notification_text))
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_beto_notification,
                getString(R.string.fgs_notification_stop),
                stopPendingIntent,
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        Timber.tag(LogTags.TTS).i("BetoForegroundService.onDestroy")
        OverlayManager.hide(this)
        lifecycleScope.launch { AgentBus.emit(AgentEvent.ServiceStopped) }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.beto.app.service.STOP"
        private const val GRACE_AFTER_TTS_MS = 400L
        // Cuando el user interrumpe a Beto tap-eando el mic, dejamos que el speaker drene
        // ~150ms (ya cortamos el TTS, no tiene sentido esperar 600ms como en el path auto).
        private const val INTERRUPT_GRACE_MS = 150L

        fun startIntent(context: Context): Intent =
            Intent(context, BetoForegroundService::class.java)
    }
}
