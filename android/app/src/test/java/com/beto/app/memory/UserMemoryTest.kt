package com.beto.app.memory

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun serializesAndDeserializesRoundTrip() {
        val original = UserMemory.empty()
            .withAlias("nieto", contact())
            .withChannelPreference(contact(), Channel.WHATSAPP)
            .withFact("familia", "Juan es mi nieto")

        val decoded = json.decodeFromString<UserMemory>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun emptyHasAllCollectionsEmpty() {
        val memory = UserMemory.empty()

        assertTrue(memory.aliases.isEmpty())
        assertTrue(memory.channelPreferences.isEmpty())
        assertTrue(memory.profile.isEmpty())
        assertEquals(1, memory.version)
    }

    @Test
    fun knowsAliasTrueAfterRecordAlias() {
        val memory = UserMemory.empty().withAlias("Nieto", contact())

        assertTrue(memory.knowsAlias("nieto"))
        assertEquals(contact(), memory.resolveAlias("NIETO"))
    }

    @Test
    fun preferredChannelReturnsNullWhenNotSet() {
        assertNull(UserMemory.empty().preferredChannel(contact()))
    }

    @Test
    fun recordFactAppendsWithoutOverwritingExistingFacts() {
        val memory = UserMemory.empty()
            .withFact("familia", "Juan es mi nieto")
            .withFact("familia", "Ana es mi hija")

        assertEquals(listOf("Juan es mi nieto", "Ana es mi hija"), memory.profile["familia"])
    }

    @Test
    fun doesNotKnowUnknownAlias() {
        assertFalse(UserMemory.empty().knowsAlias("nieto"))
    }

    private fun contact(): ContactRef =
        ContactRef(id = 1L, displayName = "Juan Perez", phoneE164 = "+541123456789")
}
