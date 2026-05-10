package com.beto.app.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.beto.app.BetoApplication
import com.beto.app.action.AgentBusVoiceCapture
import com.beto.app.memory.UserMemoryStore
import com.beto.app.ui.BetoTheme
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import timber.log.Timber

/**
 * Activity del Modo Compañero. Transparente sobre la app actual; el Compose
 * `ModalBottomSheet` ocupa el 75-90% inferior.
 */
class CompanionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(LogTags.LLM).i("CompanionActivity opened")

        setContent {
            val viewModel = remember { buildViewModel() }
            BetoTheme {
                CompanionSheet(
                    viewModel = viewModel,
                    onDismiss = { finish() },
                )
            }

            LaunchedEffect(Unit) {
                Timber.tag(LogTags.LLM).d("Companion sheet attached")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(LogTags.LLM).i("CompanionActivity closed")
    }

    private fun buildViewModel(): CompanionViewModel {
        val memory: UserMemoryStore = BetoApplication.userMemoryStore
        val voiceCapture = AgentBusVoiceCapture()
        return CompanionViewModel(
            llm = CompanionLlmClient(),
            tts = object : CompanionTtsSink {
                override suspend fun speak(text: String) {
                    TtsManager.speakAndAwait(text)
                }
            },
            voiceCapture = voiceCapture,
            memory = memory,
        )
    }
}
