package com.beto.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSelectorTest {

    private fun voice(
        name: String,
        localeTag: String? = "es-AR",
        quality: Int = 400,
        networkRequired: Boolean = false,
        features: Set<String> = emptySet(),
    ) = VoiceCandidate(name, localeTag, quality, networkRequired, features)

    @Test
    fun `prefers male neural es-AR over female neural es-AR`() {
        val tomas = voice("es-AR-Tomas-Neural")
        val elena = voice("es-AR-Elena-Neural")
        val best = VoiceSelector.selectBestCandidate(listOf(tomas, elena))
        assertEquals(tomas.name, best?.name)
    }

    @Test
    fun `prefers male neural es-AR over male non-neural es-AR`() {
        val neural = voice("es-AR-Tomas-Neural")
        val plain = voice("es-AR-male-default")
        val best = VoiceSelector.selectBestCandidate(listOf(plain, neural))
        assertEquals(neural.name, best?.name)
    }

    @Test
    fun `falls back to male neural es-419 if no male es-AR available`() {
        val mxJorge = voice("es-MX-Jorge-Neural", localeTag = "es-MX")
        val arElena = voice("es-AR-Elena-Neural", localeTag = "es-AR")
        val best = VoiceSelector.selectBestCandidate(listOf(arElena, mxJorge))
        assertEquals(mxJorge.name, best?.name)
    }

    @Test
    fun `falls back to female es as last resort`() {
        val onlyFemale = voice("es-AR-Elena-Neural")
        val best = VoiceSelector.selectBestCandidate(listOf(onlyFemale))
        assertEquals(onlyFemale.name, best?.name)
    }

    @Test
    fun `returns null if no Spanish voice at all`() {
        val enUs = voice("en-US-Standard-A", localeTag = "en-US")
        val best = VoiceSelector.selectBestCandidate(listOf(enUs))
        assertNull(best)
    }

    @Test
    fun `recognizes known male IDs as male`() {
        listOf("es-AR-Tomas-Neural", "es-MX-Jorge-Neural", "es-ES-Alvaro").forEach {
            assertEquals("$it should score 100", 100, VoiceSelector.scoreGender(it))
        }
    }

    @Test
    fun `recognizes male name hints`() {
        listOf("custom-andres-voice", "diego_male_v2", "pablo-tts").forEach {
            assertTrue("$it should be male", VoiceSelector.scoreGender(it) >= 80)
        }
    }

    @Test
    fun `recognizes female name hints as not male`() {
        listOf("es-AR-Elena", "Maria-tts", "sofia_neural").forEach {
            assertEquals("$it should score 0", 0, VoiceSelector.scoreGender(it))
        }
    }

    @Test
    fun `unknown names get neutral score`() {
        assertEquals(50, VoiceSelector.scoreGender("voice-x123"))
    }

    @Test
    fun `locale scoring orders argentina above spain`() {
        val ar = VoiceSelector.scoreLocale("es-AR")
        val es419 = VoiceSelector.scoreLocale("es-419")
        val esEs = VoiceSelector.scoreLocale("es-ES")
        assertTrue("es-AR > es-419", ar > es419)
        assertTrue("es-419 > es-ES", es419 > esEs)
    }

    @Test
    fun `non-spanish locale scores zero`() {
        assertEquals(0, VoiceSelector.scoreLocale("en-US"))
        assertEquals(0, VoiceSelector.scoreLocale(null))
    }

    @Test
    fun `network-required voice is deprioritized over offline equivalent`() {
        // Mismo género/neural/locale/quality — solo cambia network. Offline gana por tiebreaker.
        val cloud = voice("es-AR-pablo-neural-cloud", networkRequired = true)
        val offline = voice("es-AR-pablo-neural-offline", networkRequired = false)
        val best = VoiceSelector.selectBestCandidate(listOf(cloud, offline))
        assertNotNull(best)
        assertEquals(offline.name, best?.name)
    }

    @Test
    fun `neural detected via wavenet name`() {
        assertTrue(VoiceSelector.isNeural("WaveNet-V1", emptySet()))
    }

    @Test
    fun `neural detected via network-tts feature`() {
        assertTrue(VoiceSelector.isNeural("plain-name", setOf("network-tts")))
    }
}
