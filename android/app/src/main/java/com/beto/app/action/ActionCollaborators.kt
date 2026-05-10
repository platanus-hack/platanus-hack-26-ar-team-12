package com.beto.app.action

import android.content.Context
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentCommand
import com.beto.app.bus.AgentEvent
import com.beto.app.contacts.ContactInfo
import com.beto.app.memory.ContactRef
import com.beto.app.voice.TtsManager
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

fun interface Speaker {
    fun speak(text: String)
}

interface SuspendableVoiceCapture {
    suspend fun captureOnce(timeoutMs: Long): String?
}

class TtsSpeaker : Speaker {
    override fun speak(text: String) {
        TtsManager.speak(text)
    }
}

class AgentBusVoiceCapture(
    private val context: Context,
    private val markHandled: (String) -> Unit = {},
) : SuspendableVoiceCapture {
    override suspend fun captureOnce(timeoutMs: Long): String? {
        AgentBus.command(AgentCommand.StartVoiceCapture())
        return withTimeoutOrNull(timeoutMs) {
            AgentBus.events
                .filterIsInstance<AgentEvent.VoiceCaptured>()
                .first()
                .text
                .also(markHandled)
        }
    }
}

fun ContactInfo.toContactRef(): ContactRef =
    ContactRef(
        id = id,
        displayName = displayName,
        phoneE164 = phoneNumbers.firstOrNull()?.e164.orEmpty(),
    )
