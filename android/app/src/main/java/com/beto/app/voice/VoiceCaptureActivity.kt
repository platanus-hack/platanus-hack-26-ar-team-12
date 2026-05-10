package com.beto.app.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.action.DeterministicMatcher
import com.beto.app.action.MatchResult
import com.beto.app.contacts.ContactRepository
import com.beto.app.llm.GeminiLlmClient
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class VoiceCaptureActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var startedAtMs: Long = 0L
    private var launchedRecognizer = false
    // Cloud-backed STT is the recommended path per ARCHITECTURE.md (more accurate,
    // es-AR widely supported). On-device es-AR model is rarely shipped on devices,
    // and the post-speech retry path doesn't fall back, so transcripts come back empty.
    private var preferOnDeviceRecognizer = false
    private var speechStarted = false
    private var recognizer: SpeechRecognizer? = null
    private val sttCorrector by lazy { SttCorrector(GeminiLlmClient()) }
    private val contacts by lazy { ContactRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startedAtMs = intent.getLongExtra(EXTRA_STARTED_AT_MS, SystemClock.elapsedRealtime())
    }

    override fun onResume() {
        super.onResume()
        if (!launchedRecognizer) {
            launchedRecognizer = true
            launchRecognizer()
        }
    }

    private fun launchRecognizer() {
        Timber.tag(LogTags.STT).i("PLAN_C_STT_START")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(
                RecognizerIntent.EXTRA_PREFER_OFFLINE,
                preferOnDeviceRecognizer && RecognizerFactory.shouldPreferOffline(this@VoiceCaptureActivity),
            )
        }
        runCatching {
            val speechRecognizer = RecognizerFactory.create(this, preferOnDevice = preferOnDeviceRecognizer)
            recognizer = speechRecognizer
            speechRecognizer.setRecognitionListener(BetoRecognitionListener())
            speechRecognizer.startListening(intent)
        }.onFailure { error ->
            if (retryAfterOnDeviceLaunchFailure(error)) return
            emitFailure("recognizer_launch:${error::class.simpleName}")
        }
    }

    private fun handleRecognitionResult(transcript: String?, confidence: Float?) {
        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        if (transcript.isNullOrBlank()) {
            emitFailure("empty_or_cancelled", elapsedMs)
            return
        }

        Timber.tag(LogTags.STT).i(
            "PLAN_C_STT_RESULT elapsedMs=%d confidence=%s",
            elapsedMs,
            confidence?.toString() ?: "unknown",
        )
        scope.launch {
            val finalTranscript = maybeCorrectTranscript(transcript.trim(), confidence)
            AgentBus.emit(AgentEvent.VoiceCaptured(finalTranscript, elapsedMs))
            finish()
        }
    }

    private suspend fun maybeCorrectTranscript(raw: String, confidence: Float?): String {
        if (!shouldCorrect(raw, confidence)) return raw

        val correctionStartedAt = SystemClock.elapsedRealtime()
        AgentBus.emit(AgentEvent.SttCorrectionStarted(raw, confidence))
        val corrected = sttCorrector.correct(
            raw = raw,
            context = SttContext(knownContacts = contacts.knownContactNames(), lastCommand = null),
        )
        Timber.tag(LogTags.STT).i(
            "STT_CORRECTED elapsedMs=%d changed=%s",
            SystemClock.elapsedRealtime() - correctionStartedAt,
            corrected != raw,
        )
        return corrected
    }

    private fun shouldCorrect(transcript: String, confidence: Float?): Boolean {
        val lowConfidence = confidence != null && confidence < CONFIDENCE_THRESHOLD
        val noPlanCMatch = DeterministicMatcher.match(transcript) == MatchResult.NoMatch
        val shortCommand = transcript.split(Regex("\\s+")).count { it.isNotBlank() } <= SHORT_COMMAND_MAX_WORDS
        return lowConfidence || (noPlanCMatch && shortCommand)
    }

    private fun emitFailure(reason: String, elapsedMs: Long = SystemClock.elapsedRealtime() - startedAtMs) {
        Timber.tag(LogTags.STT).w("PLAN_C_STT_RESULT elapsedMs=%d failed=%s", elapsedMs, reason)
        scope.launch {
            AgentBus.emit(AgentEvent.VoiceCaptureFailed(reason, elapsedMs))
            finish()
        }
    }

    private fun retryWithNetworkRecognizer(error: Int): Boolean {
        if (!preferOnDeviceRecognizer || speechStarted) return false
        preferOnDeviceRecognizer = false
        Timber.tag(LogTags.STT).w(
            "On-device recognizer failed before speech with error=%d; retrying cloud-backed recognizer",
            error,
        )
        recognizer?.destroy()
        recognizer = null
        launchRecognizer()
        return true
    }

    private fun retryAfterOnDeviceLaunchFailure(error: Throwable): Boolean {
        if (!preferOnDeviceRecognizer) return false
        preferOnDeviceRecognizer = false
        Timber.tag(LogTags.STT).w(
            error,
            "On-device recognizer launch failed; retrying cloud-backed recognizer",
        )
        recognizer?.destroy()
        recognizer = null
        launchRecognizer()
        return true
    }

    override fun onDestroy() {
        recognizer?.destroy()
        recognizer = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class BetoRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() {
            speechStarted = true
        }
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            if (retryWithNetworkRecognizer(error)) return
            emitFailure("recognizer_error:$error")
        }

        override fun onResults(results: Bundle?) {
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
            val confidence = results
                ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                ?.firstOrNull()
                ?.takeUnless { it < 0f }
            handleRecognitionResult(transcript, confidence)
        }
    }

    companion object {
        private const val EXTRA_STARTED_AT_MS = "com.beto.app.voice.STARTED_AT_MS"
        private const val CONFIDENCE_THRESHOLD = 0.6f
        private const val SHORT_COMMAND_MAX_WORDS = 7

        fun startIntent(context: Context, startedAtMs: Long? = null): Intent =
            Intent(context, VoiceCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_STARTED_AT_MS, startedAtMs ?: SystemClock.elapsedRealtime())
    }
}
