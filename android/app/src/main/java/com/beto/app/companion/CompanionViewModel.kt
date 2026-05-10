package com.beto.app.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beto.app.action.SuspendableVoiceCapture
import com.beto.app.memory.UserMemoryStore
import com.beto.app.util.LogTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

interface CompanionTtsSink {
    suspend fun speak(text: String)
}

/**
 * ViewModel del Modo Compañero. Estado:
 *  - `messages`: lista de mensajes de la sesión actual (cap 20).
 *  - `isThinking`: indica que el LLM está procesando.
 *  - `isCapturingVoice`: indica que se está esperando entrada de voz.
 *
 * Privacidad (COMP-04):
 *  - Historial NO persiste entre sesiones — al cerrar la sheet se descarta.
 *  - Profile facts solo se guardan si el usuario confirma explícitamente con el botón Sí.
 */
class CompanionViewModel(
    private val llm: CompanionChatClient,
    private val tts: CompanionTtsSink,
    private val voiceCapture: SuspendableVoiceCapture,
    private val memory: UserMemoryStore,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<CompanionMessage>>(emptyList())
    val messages: StateFlow<List<CompanionMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isCapturingVoice = MutableStateFlow(false)
    val isCapturingVoice: StateFlow<Boolean> = _isCapturingVoice.asStateFlow()

    fun sendUserText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { handleUserMessage(trimmed) }
    }

    fun startVoiceInput() {
        viewModelScope.launch {
            _isCapturingVoice.value = true
            val captured = try {
                voiceCapture.captureOnce(VOICE_CAPTURE_TIMEOUT_MS)
            } finally {
                _isCapturingVoice.value = false
            }
            if (!captured.isNullOrBlank()) {
                handleUserMessage(captured.trim())
            }
        }
    }

    fun confirmFact(messageId: String, fact: ProfileFact, confirmed: Boolean) {
        viewModelScope.launch {
            if (confirmed) {
                memory.recordFact(fact.category, fact.fact)
                Timber.tag(LogTags.MEMORY).d("COMPANION_FACT_SAVED category=%s", fact.category)
            }
            // Quitar la confirmación pendiente del mensaje (independientemente de la respuesta)
            _messages.value = _messages.value.map {
                if (it.id == messageId) it.copy(pendingFactConfirmation = null) else it
            }
        }
    }

    fun forgetSession() {
        _messages.value = emptyList()
        Timber.tag(LogTags.MEMORY).d("COMPANION_SESSION_CLEARED")
    }

    private suspend fun handleUserMessage(text: String) {
        appendMessage(CompanionMessage(role = Role.USER, text = text))
        _isThinking.value = true
        val reply = try {
            llm.chat(_messages.value)
        } catch (e: Exception) {
            Timber.tag(LogTags.LLM).w(e, "Companion chat error")
            "Disculpame, no me salió bien. Probemos de nuevo."
        } finally {
            _isThinking.value = false
        }

        val fact = runCatching { llm.extractProfileFact(text) }.getOrNull()
        appendMessage(
            CompanionMessage(
                role = Role.BETO,
                text = reply,
                pendingFactConfirmation = fact,
            ),
        )

        runCatching { tts.speak(reply) }
    }

    private fun appendMessage(msg: CompanionMessage) {
        _messages.value = (_messages.value + msg).takeLast(MAX_HISTORY)
    }

    companion object {
        private const val MAX_HISTORY = 20
        private const val VOICE_CAPTURE_TIMEOUT_MS = 15_000L
    }
}
