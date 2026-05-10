package com.beto.app.action

import com.beto.app.memory.Channel
import com.beto.app.memory.ContactRef
import com.beto.app.memory.UserMemoryStore
import com.beto.app.util.LogTags
import timber.log.Timber
import java.util.Locale

class ChannelClarifier(
    private val speaker: Speaker,
    private val voiceCapture: SuspendableVoiceCapture,
    private val memory: UserMemoryStore,
) {
    suspend fun clarify(contact: ContactRef): Channel? {
        repeat(MAX_ATTEMPTS) {
            speaker.speak("¿Por WhatsApp, SMS o llamada?")
            val response = voiceCapture.captureOnce(CHANNEL_CAPTURE_TIMEOUT_MS)
                ?.lowercase(Locale("es", "AR"))
                .orEmpty()
            val channel = parseChannel(response)
            if (channel != null) {
                memory.recordChannel(contact, channel)
                Timber.tag(LogTags.ACTION).d(
                    "DISPATCH_CLARIFY_CHANNEL contact=%d channel=%s",
                    contact.id,
                    channel,
                )
                return channel
            }
            speaker.speak("Decime por dónde — WhatsApp, mensaje o llamada.")
        }
        speaker.speak("Mejor lo intentamos en otro momento.")
        return null
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        private const val CHANNEL_CAPTURE_TIMEOUT_MS = 8_000L

        fun parseChannel(response: String): Channel? =
            when {
                "whatsapp" in response || "wasap" in response -> Channel.WHATSAPP
                "sms" in response || "mensaje" in response -> Channel.SMS
                "llamad" in response || "telefon" in response || "llamar" in response -> Channel.CALL
                else -> null
            }
    }
}
