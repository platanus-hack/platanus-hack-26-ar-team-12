package com.beto.app.companion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel del chat unificado.
 *
 * Beto es UN SOLO agente: el chat NO invoca ningún LLM directamente. Cuando el usuario
 * envía un mensaje (texto o voz), emitimos `ChatMessageSent` al bus → el ForegroundService
 * lo rutea por el `ActionDispatcher`. El dispatcher habla via TtsSpeaker que también
 * mirrorea cada respuesta como `BetoReplied` — escuchamos ese evento y lo agregamos al
 * historial visible.
 *
 * Privacidad: historial NO persiste entre sesiones (cap MAX_HISTORY).
 */
class CompanionViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<CompanionMessage>>(emptyList())
    val messages: StateFlow<List<CompanionMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isCapturingVoice = MutableStateFlow(false)
    val isCapturingVoice: StateFlow<Boolean> = _isCapturingVoice.asStateFlow()

    init {
        // Escuchamos las respuestas del agente unificado para mostrarlas en el chat.
        viewModelScope.launch {
            AgentBus.events
                .filterIsInstance<AgentEvent.BetoReplied>()
                .collect { event ->
                    appendMessage(CompanionMessage(role = Role.BETO, text = event.text))
                    _isThinking.value = false
                }
        }
        // VoiceCaptured se intercepta para mostrar lo transcripto como mensaje del usuario
        // y disparar el dispatch via ChatMessageSent.
        viewModelScope.launch {
            AgentBus.events
                .filterIsInstance<AgentEvent.VoiceCaptured>()
                .collect { event ->
                    if (event.text.isNotBlank()) {
                        sendUserText(event.text)
                    }
                    _isCapturingVoice.value = false
                }
        }
        viewModelScope.launch {
            AgentBus.events
                .filterIsInstance<AgentEvent.VoiceCaptureFailed>()
                .collect { _isCapturingVoice.value = false }
        }
        viewModelScope.launch {
            AgentBus.events
                .filterIsInstance<AgentEvent.VoiceCaptureStarted>()
                .collect { _isCapturingVoice.value = true }
        }
    }

    fun sendUserText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        appendMessage(CompanionMessage(role = Role.USER, text = trimmed))
        _isThinking.value = true
        viewModelScope.launch {
            Timber.tag(LogTags.LLM).d("CHAT_SEND text=%s", trimmed)
            AgentBus.emit(AgentEvent.ChatMessageSent(trimmed))
        }
    }

    fun startVoiceInput() {
        _isCapturingVoice.value = true
        viewModelScope.launch {
            AgentBus.command(com.beto.app.bus.AgentCommand.StartVoiceCapture())
        }
    }

    fun stopVoiceInput() {
        viewModelScope.launch {
            AgentBus.command(com.beto.app.bus.AgentCommand.StopVoiceCapture)
        }
    }

    fun forgetSession() {
        _messages.value = emptyList()
        Timber.tag(LogTags.MEMORY).d("COMPANION_SESSION_CLEARED")
    }

    private fun appendMessage(msg: CompanionMessage) {
        _messages.value = (_messages.value + msg).takeLast(MAX_HISTORY)
    }

    companion object {
        private const val MAX_HISTORY = 30
    }
}
