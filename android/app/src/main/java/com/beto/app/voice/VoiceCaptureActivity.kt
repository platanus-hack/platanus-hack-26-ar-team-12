package com.beto.app.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognizerIntent
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
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
        }
        runCatching {
            startActivityForResult(intent, REQUEST_RECOGNIZE_SPEECH)
        }.onFailure { error ->
            emitFailure("recognizer_launch:${error::class.simpleName}")
        }
    }

    @Deprecated("Activity Result API replacement is deferred; this keeps min wiring compact.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_RECOGNIZE_SPEECH) return

        val elapsedMs = SystemClock.elapsedRealtime() - startedAtMs
        val transcript = data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()

        if (resultCode == RESULT_OK && !transcript.isNullOrBlank()) {
            Timber.tag(LogTags.STT).i("PLAN_C_STT_RESULT elapsedMs=%d", elapsedMs)
            scope.launch {
                AgentBus.emit(AgentEvent.VoiceCaptured(transcript, elapsedMs))
                finish()
            }
        } else {
            emitFailure("empty_or_cancelled", elapsedMs)
        }
    }

    private fun emitFailure(reason: String, elapsedMs: Long = SystemClock.elapsedRealtime() - startedAtMs) {
        Timber.tag(LogTags.STT).w("PLAN_C_STT_RESULT elapsedMs=%d failed=%s", elapsedMs, reason)
        scope.launch {
            AgentBus.emit(AgentEvent.VoiceCaptureFailed(reason, elapsedMs))
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_RECOGNIZE_SPEECH = 2401
        private const val EXTRA_STARTED_AT_MS = "com.beto.app.voice.STARTED_AT_MS"

        fun startIntent(context: Context, startedAtMs: Long? = null): Intent =
            Intent(context, VoiceCaptureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_STARTED_AT_MS, startedAtMs ?: SystemClock.elapsedRealtime())
    }
}
