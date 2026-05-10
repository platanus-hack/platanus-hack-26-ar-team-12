package com.beto.app.action

import com.beto.app.contacts.ContactRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactClarifierTest {

    @Test
    fun singleMatchAutoResolvesAndPersists() = runBlocking {
        val memory = memoryStore()
        val clarifier = ContactClarifier(
            speaker = FakeSpeaker(),
            contacts = fakeContactRepository(listOf(ContactRow(1, "Juan Perez"))),
            memory = memory,
            voiceCapture = QueueVoiceCapture(listOf("Juan")),
        )

        val ref = clarifier.clarify("nieto")

        assertEquals("Juan Perez", ref?.displayName)
        assertEquals(ref, memory.resolveAlias("nieto"))
    }

    @Test
    fun multipleMatchesAsksWhich() = runBlocking {
        val speaker = FakeSpeaker()
        val clarifier = ContactClarifier(
            speaker = speaker,
            contacts = fakeContactRepository(listOf(ContactRow(1, "Carlos Gomez"), ContactRow(2, "Carlos Mendez"))),
            memory = memoryStore(),
            voiceCapture = QueueVoiceCapture(listOf("Carlos", "Mendez")),
        )

        val ref = clarifier.clarify("Carlos")

        assertEquals("Carlos Mendez", ref?.displayName)
        assertTrue(speaker.spoken.any { it.contains("¿Cuál?") })
    }

    @Test
    fun zeroMatchesAbortsAfterMaxAttempts() = runBlocking {
        val speaker = FakeSpeaker()
        val clarifier = ContactClarifier(
            speaker = speaker,
            contacts = fakeContactRepository(emptyList()),
            memory = memoryStore(),
            voiceCapture = QueueVoiceCapture(listOf("uno", "dos", "tres")),
        )

        val ref = clarifier.clarify("nieto")

        assertNull(ref)
        assertTrue(speaker.spoken.contains("Mejor lo intentamos en otro momento."))
    }
}
