package com.beto.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

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
        tts = TextToSpeech(context.applicationContext) { status ->
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
            tts?.setOnUtteranceProgressListener(progressListener)
            isReady = true
            flushPending()
        }
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
        val res = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        if (res == TextToSpeech.SUCCESS) {
            Timber.tag(LogTags.TTS).d("speak ok id=%s text=%s", id, text)
        } else {
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
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            // emit AgentEvent.TtsSpoke con el texto reproducido
            // (no tenemos el texto acá — el id sirve solo de correlación;
            // emitimos un evento neutral por ahora; Phase 2-3 puede mapear id->texto si necesario)
            scope.launch {
                AgentBus.emit(AgentEvent.TtsSpoke("utterance:$utteranceId"))
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            emitFailed("utterance_error:$utteranceId")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            emitFailed("utterance_error:$utteranceId:$errorCode")
        }
    }

    private fun emitFailed(reason: String) {
        scope.launch { AgentBus.emit(AgentEvent.TtsFailed(reason)) }
    }
}
