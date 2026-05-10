package com.beto.app.action

import com.beto.app.memory.Channel
import com.beto.app.memory.ContactRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelClarifierTest {

    @Test
    fun whatsappKeywordRecognizesWhatsapp() = runBlocking {
        assertEquals(Channel.WHATSAPP, clarifyWith("whatsapp"))
    }

    @Test
    fun smsKeywordRecognizesSms() = runBlocking {
        assertEquals(Channel.SMS, clarifyWith("sms"))
    }

    @Test
    fun llamadaAndTelefonoRecognizeCall() = runBlocking {
        assertEquals(Channel.CALL, clarifyWith("llamada"))
        assertEquals(Channel.CALL, clarifyWith("telefono"))
    }

    @Test
    fun unrecognizedResponseRetriesOnce() = runBlocking {
        val speaker = FakeSpeaker()
        val memory = memoryStore()
        val contact = contact()
        val clarifier = ChannelClarifier(
            speaker = speaker,
            voiceCapture = QueueVoiceCapture(listOf("no se", "mensaje")),
            memory = memory,
        )

        val result = clarifier.clarify(contact)

        assertEquals(Channel.SMS, result)
        assertTrue(speaker.spoken.any { it.contains("Decime por dónde") })
    }

    @Test
    fun abortsAfterRetriesWithWarmPhrase() = runBlocking {
        val speaker = FakeSpeaker()
        val clarifier = ChannelClarifier(
            speaker = speaker,
            voiceCapture = QueueVoiceCapture(listOf("x", "y", "z")),
            memory = memoryStore(),
        )

        val result = clarifier.clarify(contact())

        assertNull(result)
        assertTrue(speaker.spoken.contains("Mejor lo intentamos en otro momento."))
    }

    private suspend fun clarifyWith(response: String): Channel? {
        val memory = memoryStore()
        val contact = contact()
        return ChannelClarifier(FakeSpeaker(), QueueVoiceCapture(listOf(response)), memory).clarify(contact)
    }

    private fun contact(): ContactRef = ContactRef(1, "Juan", "+5411000000")
}
