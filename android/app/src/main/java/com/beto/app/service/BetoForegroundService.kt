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
import com.beto.app.actions.ContactsResolver
import com.beto.app.actions.IntentActionExecutor
import com.beto.app.actions.VoiceCommandController
import com.beto.app.llm.LLMRouter
import com.beto.app.llm.OfflineIntentClassifier
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentCommand
import com.beto.app.bus.AgentEvent
import com.beto.app.overlay.OverlayManager
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import com.beto.app.voice.VoiceCaptureActivity
import com.beto.app.voice.WakeWordDetector
import kotlinx.coroutines.launch
import timber.log.Timber

class BetoForegroundService : LifecycleService() {

    private var bootGreetingPlayed = false
    private lateinit var voiceCommandController: VoiceCommandController
    private lateinit var wakeWordDetector: WakeWordDetector

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(LogTags.TTS).i("BetoForegroundService.onCreate")
        BetoApplication.ensureNotificationChannel(this)
        startForegroundCorrectly()

        val contactsResolver = ContactsResolver(this)
        val actionExecutor = IntentActionExecutor(this, contactsResolver)
        val classifier = OfflineIntentClassifier()
        val llmRouter = LLMRouter()

        voiceCommandController = VoiceCommandController(
            scope = lifecycleScope,
            sendCommand = AgentBus::command,
            classifier = classifier,
            llmRouter = llmRouter,
            contactsResolver = contactsResolver,
            actionExecutor = actionExecutor
        )

        wakeWordDetector = WakeWordDetector(this)

        lifecycleScope.launch {
            AgentBus.commands.collect { command ->
                when (command) {
                    is AgentCommand.Speak -> {
                        Timber.tag(LogTags.TTS).d("Cmd Speak -> %s", command.text)
                        TtsManager.speak(command.text)
                    }
                    is AgentCommand.StartVoiceCapture -> {
                        wakeWordDetector.stopListening()
                        startActivity(VoiceCaptureActivity.startIntent(this@BetoForegroundService, command.startedAtMs))
                    }
                }
            }
        }

        lifecycleScope.launch {
            AgentBus.events.collect { event ->
                when (event) {
                    is AgentEvent.BubbleTapped -> {
                        Timber.tag(LogTags.STT).i("Bubble tapped or WakeWord detected -> voice capture")
                        lifecycleScope.launch { AgentBus.command(AgentCommand.StartVoiceCapture(event.startedAtMs)) }
                    }
                    is AgentEvent.VoiceCaptured -> {
                        voiceCommandController.onVoiceCaptured(event.text)
                        wakeWordDetector.startListening()
                    }
                    is AgentEvent.VoiceCaptureFailed -> {
                        Timber.tag(LogTags.STT).w("Voice capture failed: %s", event.reason)
                        TtsManager.speak("No te escuché bien. Probemos de nuevo, dale.")
                        wakeWordDetector.startListening()
                    }
                    is AgentEvent.BubbleLongPressed -> {
                        Timber.tag(LogTags.TTS).i("Bubble long-pressed -> stopping Beto")
                        stopSelf()
                    }
                    is AgentEvent.BubbleCloseRequested -> {
                        Timber.tag(LogTags.TTS).i("Bubble close requested -> stopping Beto")
                        stopSelf()
                    }
                    else -> Unit
                }
            }
        }

        wakeWordDetector.startListening()
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
        wakeWordDetector.stopListening()
        OverlayManager.hide(this)
        lifecycleScope.launch { AgentBus.emit(AgentEvent.ServiceStopped) }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.beto.app.service.STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, BetoForegroundService::class.java)
    }
}
