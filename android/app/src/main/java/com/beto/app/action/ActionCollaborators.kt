package com.beto.app.action

import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentCommand
import com.beto.app.bus.AgentEvent
import com.beto.app.contacts.ContactInfo
import com.beto.app.memory.ContactRef
import com.beto.app.voice.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

interface Speaker {
    fun speak(text: String)

    /** Habla y suspende hasta que TTS termine. Usado por clarifiers para evitar que el mic
     *  se abra mientras Beto sigue hablando (bucle de auto-escucha). */
    suspend fun speakAndAwait(text: String) { speak(text) }
}

interface SuspendableVoiceCapture {
    suspend fun captureOnce(timeoutMs: Long): String?
}

class TtsSpeaker : Speaker {
    private val emitScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun speak(text: String) {
        // Mirroreamos al bus para que el chat unificado pueda mostrar la respuesta de Beto
        // como bubble. Bus es no-op cuando no hay chat abierto.
        if (text.isNotBlank()) {
            emitScope.launch { AgentBus.emit(AgentEvent.BetoReplied(text)) }
        }
        TtsManager.speak(text)
    }
}

class AgentBusVoiceCapture(
    private val onCaptureStarted: () -> Unit = {},
    private val onCaptured: (String) -> Unit = {},
    private val onCaptureFinished: () -> Unit = {},
) : SuspendableVoiceCapture {
    override suspend fun captureOnce(timeoutMs: Long): String? {
        onCaptureStarted()
        AgentBus.command(AgentCommand.StartVoiceCapture())
        return try {
            withTimeoutOrNull(timeoutMs) {
                AgentBus.events
                    .filterIsInstance<AgentEvent.VoiceCaptured>()
                    .first()
                    .text
                    .also(onCaptured)
            }
        } finally {
            onCaptureFinished()
        }
    }
}

fun ContactInfo.toContactRef(): ContactRef =
    ContactRef(
        id = id,
        displayName = displayName,
        phoneE164 = phoneNumbers.firstOrNull()?.e164.orEmpty(),
    )
