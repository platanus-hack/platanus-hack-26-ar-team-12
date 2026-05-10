package com.beto.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Detector de Wake Word "Beto" en segundo plano con "Escucha Activa Infinita".
 * (D-08) Se comporta como Siri/Ok Google, reiniciándose ante silencios o errores.
 */
class WakeWordDetector(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var shouldBeListening = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Timber.tag(LogTags.STT).d("WakeWord: Oído abierto y atento")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // Error 7 es No Match / Silencio. Error 6 es Timeout.
            // Son normales en escucha pasiva, reiniciamos.
            Timber.tag(LogTags.STT).d("WakeWord pausa (code=%d) — Reiniciando...", error)
            restartListeningWithDelay()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase() ?: ""
            
            if (text.contains("beto")) {
                Timber.tag(LogTags.STT).i("WAKE WORD DETECTADO: Beto")
                shouldBeListening = false // Detenemos la escucha pasiva para pasar al comando real
                scope.launch { AgentBus.emit(AgentEvent.BubbleTapped(System.currentTimeMillis())) }
            } else {
                // Si escuchó ruido u otra palabra, seguimos atentos
                restartListeningWithDelay()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase() ?: ""
            
            if (text.contains("beto")) {
                Timber.tag(LogTags.STT).i("WAKE WORD DETECTADO (Parcial): Beto")
                shouldBeListening = false
                destroyRecognizer() // Matamos rápido para que no haya colisión con el STT principal
                scope.launch { AgentBus.emit(AgentEvent.BubbleTapped(System.currentTimeMillis())) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (shouldBeListening) return
        shouldBeListening = true
        Timber.tag(LogTags.STT).i("Iniciando modo manos libres (Wake Word)")
        internalStart()
    }

    private fun internalStart() {
        if (!shouldBeListening) return

        destroyRecognizer() // Limpieza previa

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Esto ayuda a que no tire error tan rápido por silencio
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Timber.tag(LogTags.STT).e(e, "Fallo al iniciar SpeechRecognizer")
            restartListeningWithDelay()
        }
    }

    private fun restartListeningWithDelay() {
        if (!shouldBeListening) return
        
        destroyRecognizer()
        
        scope.launch {
            delay(500) // Evitamos saturar el servicio de Google
            if (shouldBeListening) {
                internalStart()
            }
        }
    }

    fun stopListening() {
        Timber.tag(LogTags.STT).i("Deteniendo modo manos libres")
        shouldBeListening = false
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
