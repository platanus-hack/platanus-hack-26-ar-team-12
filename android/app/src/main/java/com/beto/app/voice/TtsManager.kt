package com.beto.app.voice

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Singleton TTS — init en BetoApplication.onCreate(), cola interna pre-init para evitar
 * Pitfall #3 (TTS race condition: primer speak() se pierde porque onInit no completó).
 *
 * Cascada de Locale (D-10): es-AR → es-419 → es-ES → es → en-US.
 *
 * Frase de boot exacta (D-10): "Hola, soy Beto. Estoy acá para ayudarte."
 */
object TtsManager {
    @Volatile
    var isReady: Boolean = false
        private set

    private var tts: TextToSpeech? = null
    private val pendingQueue: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private val utteranceCounter = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pendingContinuations: ConcurrentHashMap<String, CancellableContinuation<Unit>> = ConcurrentHashMap()
    private val activeUtterances = AtomicInteger(0)
    @Volatile private var lastUtteranceFinishedAtMs: Long = 0L
    @Volatile private var previewRestoreJob: Job? = null
    private var selectedVoiceName: String? = null
    @Volatile var selectedVoiceIsLikelyMale: Boolean = false
        private set

    /**
     * Margen de seguridad después de `onDone` para que el speaker termine de drenar el
     * audio buffer antes de que el SpeechRecognizer arranque. Sin esto, el SR captura las
     * últimas sílabas de Beto. Calibrado contra el teléfono de demo (Android 13).
     */
    private const val SPEAKER_DRAIN_GRACE_MS = 600L

    private val LOCALE_CASCADE = listOf(
        Locale("es", "AR"),
        Locale("es", "419"),  // Spanish — Latin America (algunos engines la prefieren)
        Locale("es", "ES"),
        Locale("es"),
        Locale("en", "US"),
    )

    fun init(context: Context) {
        if (tts != null) {
            Timber.tag(LogTags.TTS).d("init() called twice — ignoring")
            return
        }
        Timber.tag(LogTags.TTS).d("init() — creating TextToSpeech")
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Timber.tag(LogTags.TTS).e("onInit FAIL status=%d", status)
                emitFailed("init_failed:$status")
                return@TextToSpeech
            }
            val chosen = pickFirstAvailableLocale()
            if (chosen == null) {
                Timber.tag(LogTags.TTS).e("Ningún Locale de la cascada disponible")
                emitFailed("no_locale_available")
                return@TextToSpeech
            }
            Timber.tag(LogTags.TTS).i("onInit SUCCESS — locale=%s", chosen)
            applyBestVoice(appContext)
            tts?.setOnUtteranceProgressListener(progressListener)
            isReady = true
            flushPending()
        }
    }

    /**
     * Después de fijar el locale, intenta seleccionar la mejor voz **masculina + neural + argentina**.
     * Loguea la voz elegida con `VOICE_SELECTED` o un warning si la heurística no encontró masculina
     * (para revisar manualmente al setup del device de demo).
     */
    private fun applyBestVoice(context: Context? = null) {
        val engine = tts ?: return
        val voices = runCatching { engine.voices.orEmpty() }.getOrElse { emptySet() }
        // Diagnóstico: listar voces es-* disponibles.
        voices.filter { it.locale?.language == "es" }.forEach { v ->
            Timber.tag(LogTags.TTS).d(
                "VOICE_AVAILABLE name=%s locale=%s quality=%s network=%s features=%s",
                v.name, v.locale, v.quality, v.isNetworkConnectionRequired, v.features,
            )
        }

        // Si el usuario eligió una voz manualmente (Settings de voz), respetá esa elección.
        val savedName = context?.let { UserVoicePreferences.savedVoiceName(it) }
        val userChosen = savedName?.let { name -> voices.firstOrNull { it.name == name } }

        val chosen = userChosen ?: VoiceSelector.selectBest(voices)
        if (chosen == null) {
            Timber.tag(LogTags.TTS).w("VOICE_SELECTED none — no Spanish voice found")
            return
        }
        val res = engine.setVoice(chosen)
        if (res != TextToSpeech.SUCCESS) {
            Timber.tag(LogTags.TTS).w("setVoice failed code=%d voice=%s", res, chosen.name)
            return
        }
        selectedVoiceName = chosen.name
        selectedVoiceIsLikelyMale = VoiceSelector.isLikelyMale(chosen)
        Timber.tag(LogTags.TTS).i(
            "VOICE_SELECTED name=%s locale=%s likelyMale=%s networkRequired=%s userOverride=%s",
            chosen.name, chosen.locale, selectedVoiceIsLikelyMale, chosen.isNetworkConnectionRequired,
            userChosen != null,
        )
        if (userChosen == null && !selectedVoiceIsLikelyMale) {
            Timber.tag(LogTags.TTS).w(
                "VOICE_GENDER_FALLBACK selected=%s preferred=male — el user puede elegir manual desde Settings",
                chosen.name,
            )
        }
    }

    /**
     * Lista todas las voces es-* disponibles. Usado por Settings de voz.
     */
    fun allSpanishVoices(): List<android.speech.tts.Voice> =
        tts?.voices.orEmpty().filter { it.locale?.language == "es" }

    /**
     * Fallback: si no hay voces es-*, devolvemos TODAS las voces que el engine reporte.
     * Algunos dispositivos no setean `locale.language` correctamente o solo tienen voces
     * genéricas que igual funcionan en español pasable.
     */
    fun allVoicesAnyLanguage(): List<android.speech.tts.Voice> =
        tts?.voices.orEmpty().toList()

    /** Diagnóstico: cuántas voces detectó el engine en total y cuántas en es-*. */
    fun voicesDiagnostic(): String {
        val all = tts?.voices.orEmpty()
        val es = all.filter { it.locale?.language == "es" }
        return "engine=${if (tts == null) "null" else "ok"} ready=$isReady total=${all.size} es=${es.size}"
    }

    /**
     * Reproduce un texto corto con una voz específica. Al terminar el preview NO recuperamos
     * la "voz anterior" (eso causaba el bug donde tocar Probar→Elegir dentro del delay de
     * restore pisaba la elección del user). En cambio, reaplicamos `applyBestVoice(context)`
     * que ya respeta el override guardado del user o cae al automático.
     *
     * Si llega un nuevo preview o un `applyUserPreferenceOrAuto` mientras este restore está
     * pendiente, lo cancelamos para que no sobreescriba la elección oficial.
     */
    fun previewVoice(voice: android.speech.tts.Voice, text: String, context: Context) {
        previewRestoreJob?.cancel()
        previewRestoreJob = null
        val engine = tts ?: return
        val appContext = context.applicationContext
        runCatching { engine.setVoice(voice) }
        val id = "beto-preview-${utteranceCounter.incrementAndGet()}"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        previewRestoreJob = scope.launch {
            delay(3500)
            applyBestVoice(appContext)
            previewRestoreJob = null
        }
    }

    /**
     * Re-aplica la preferencia del usuario o vuelve al automático. Llamar después de
     * cambiar la elección desde el Settings de voz.
     *
     * Importante: cancela cualquier preview-restore pendiente para que el delayed `setVoice`
     * del preview no pise la elección oficial.
     */
    fun applyUserPreferenceOrAuto(context: Context) {
        previewRestoreJob?.cancel()
        previewRestoreJob = null
        applyBestVoice(context)
    }

    /** Snapshot de la voz seleccionada (para debug / pre-flight check). */
    fun selectedVoiceSummary(): String? = selectedVoiceName?.let {
        "$it (likelyMale=$selectedVoiceIsLikelyMale)"
    }

    private fun pickFirstAvailableLocale(): Locale? {
        val engine = tts ?: return null
        for (locale in LOCALE_CASCADE) {
            val res = engine.setLanguage(locale)
            if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                return locale
            }
            Timber.tag(LogTags.TTS).w("Locale %s no disponible (code=%d)", locale, res)
        }
        return null
    }

    /**
     * Habla un texto. Si TTS no está listo todavía, encola y se reproduce en onInit SUCCESS.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        if (!isReady) {
            Timber.tag(LogTags.TTS).d("speak() pre-init — encolando: %s", text)
            pendingQueue.add(text)
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val id = "beto-utt-${utteranceCounter.incrementAndGet()}"
        activeUtterances.incrementAndGet()
        val res = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        if (res == TextToSpeech.SUCCESS) {
            Timber.tag(LogTags.TTS).d("speak ok id=%s text=%s", id, text)
        } else {
            activeUtterances.decrementAndGet()
            Timber.tag(LogTags.TTS).e("speak fail res=%s text=%s", res, text)
            emitFailed("speak_failed:$res")
        }
    }

    private fun flushPending() {
        while (true) {
            val next = pendingQueue.poll() ?: break
            Timber.tag(LogTags.TTS).d("flushPending -> %s", next)
            speakNow(next)
        }
    }

    /**
     * Habla un texto y suspende hasta que el TTS termine (onDone/onError).
     * Usado por clarificadores (Phase 3) y Modo Guía (Phase 4-04) que necesitan
     * encadenar voz → captura → voz sin pisarse.
     *
     * Si TTS no está listo, encola con `speak()` y completa inmediato (degradación
     * elegante — no queremos colgar la coroutine si TTS fallo init).
     */
    suspend fun speakAndAwait(text: String) {
        if (text.isBlank()) return
        if (!isReady) {
            Timber.tag(LogTags.TTS).w("speakAndAwait pre-init — encolando sin esperar")
            pendingQueue.add(text)
            return
        }
        suspendCancellableCoroutine<Unit> { cont ->
            val id = "beto-await-${utteranceCounter.incrementAndGet()}"
            pendingContinuations[id] = cont
            activeUtterances.incrementAndGet()
            cont.invokeOnCancellation {
                pendingContinuations.remove(id)
                activeUtterances.decrementAndGet()
                runCatching { tts?.stop() }
            }
            val res = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
            if (res != TextToSpeech.SUCCESS) {
                pendingContinuations.remove(id)
                activeUtterances.decrementAndGet()
                Timber.tag(LogTags.TTS).e("speakAndAwait fail res=%s text=%s", res, text)
                if (cont.isActive) cont.resume(Unit)
            } else {
                Timber.tag(LogTags.TTS).d("speakAndAwait ok id=%s text=%s", id, text)
            }
        }
    }

    /**
     * `true` si TTS está actualmente reproduciendo, en cola, o si terminó hace muy poco
     * (menos de [SPEAKER_DRAIN_GRACE_MS]). El grace cubre la latencia entre el callback
     * `onDone` (que dispara cuando se generó el último sample) y el momento en que el
     * speaker dejó de reproducir físicamente.
     *
     * Lo usa BetoForegroundService para decidir si abrir el mic ya o esperar — sin esto,
     * el SpeechRecognizer arrancaría y capturaría la última palabra de Beto.
     */
    fun isBusySpeaking(): Boolean {
        if (activeUtterances.get() > 0) return true
        if (lastUtteranceFinishedAtMs == 0L) return false
        return SystemClock.elapsedRealtime() - lastUtteranceFinishedAtMs < SPEAKER_DRAIN_GRACE_MS
    }

    /**
     * Detiene cualquier utterance en curso o en cola y limpia el estado interno.
     * Lo usa el flujo de "interrupt" cuando el user toca el mic mientras Beto habla:
     * queremos que el user nos pueda interrumpir, no que pelee con la voz de Beto.
     */
    fun stopAll() {
        Timber.tag(LogTags.TTS).i("stopAll() — interrupting any pending utterance/queue")
        runCatching { tts?.stop() }
        pendingQueue.clear()
        // Resume cualquier continuation pendiente para que speakAndAwait no quede colgado.
        pendingContinuations.values.toList().forEach { resumeIfActive(it) }
        pendingContinuations.clear()
        activeUtterances.set(0)
        lastUtteranceFinishedAtMs = SystemClock.elapsedRealtime()
    }

    /** Frase de boot exacta de D-10 — pronunciada por BetoForegroundService al primer start. */
    fun speakBootGreeting() {
        speak("Hola, soy Beto. Estoy acá para ayudarte.")
    }

    fun shutdown() {
        Timber.tag(LogTags.TTS).i("shutdown")
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            utteranceId?.let { id ->
                scope.launch { AgentBus.emit(AgentEvent.TtsStarted(id)) }
            }
        }
        override fun onDone(utteranceId: String?) {
            markUtteranceFinished()
            // Resume any pending continuation registered by speakAndAwait(). El listener es global,
            // así que cualquier utterance —incluyendo speak() normal— va a llegar acá. Si no hay
            // continuation registrada para el id, simplemente emitimos el evento como antes.
            utteranceId?.let { id -> pendingContinuations.remove(id)?.let { resumeIfActive(it) } }
            scope.launch {
                AgentBus.emit(AgentEvent.TtsSpoke("utterance:$utteranceId"))
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            markUtteranceFinished()
            utteranceId?.let { id -> pendingContinuations.remove(id)?.let { resumeIfActive(it) } }
            emitFailed("utterance_error:$utteranceId")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            markUtteranceFinished()
            utteranceId?.let { id -> pendingContinuations.remove(id)?.let { resumeIfActive(it) } }
            emitFailed("utterance_error:$utteranceId:$errorCode")
        }
    }

    private fun markUtteranceFinished() {
        // Decrement defensively (no bajamos de 0) y refrescá el timestamp para el grace de drain.
        activeUtterances.updateAndGet { current -> if (current > 0) current - 1 else 0 }
        lastUtteranceFinishedAtMs = SystemClock.elapsedRealtime()
    }

    private fun resumeIfActive(cont: CancellableContinuation<Unit>) {
        if (cont.isActive) cont.resume(Unit)
    }

    private fun emitFailed(reason: String) {
        scope.launch { AgentBus.emit(AgentEvent.TtsFailed(reason)) }
    }
}
