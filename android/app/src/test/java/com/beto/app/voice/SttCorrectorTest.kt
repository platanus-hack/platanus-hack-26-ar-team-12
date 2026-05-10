package com.beto.app.voice

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SttCorrectorTest {

    @Test
    fun correctsPhoneticConfusionOfContactName() = runBlocking {
        val corrector = SttCorrector(FakeCorrectionClient("""{"corrected":"llama a juan"}"""))

        val result = corrector.correct(
            raw = "llama a juana",
            context = SttContext(knownContacts = listOf("Juan", "Pedro"), lastCommand = null),
        )

        assertEquals("llama a juan", result)
    }

    @Test
    fun doesNotInventContactNotInKnownList() = runBlocking {
        val corrector = SttCorrector(FakeCorrectionClient("""{"corrected":"llama a juan"}"""))

        val result = corrector.correct(
            raw = "llama a marcelo",
            context = SttContext(knownContacts = listOf("Juan"), lastCommand = null),
        )

        assertEquals("llama a marcelo", result)
    }

    @Test
    fun rejectsUnknownCorrectedContact() = runBlocking {
        val corrector = SttCorrector(FakeCorrectionClient("""{"corrected":"llama a martin"}"""))

        val result = corrector.correct(
            raw = "llama a marcelo",
            context = SttContext(knownContacts = listOf("Juan"), lastCommand = null),
        )

        assertEquals("llama a marcelo", result)
    }

    @Test
    fun doesNotChangeVerb() = runBlocking {
        val corrector = SttCorrector(FakeCorrectionClient("""{"corrected":"mandale a juan"}"""))

        val result = corrector.correct(
            raw = "llamale a juan",
            context = SttContext(knownContacts = listOf("Juan"), lastCommand = null),
        )

        assertEquals("llamale a juan", result)
    }

    @Test
    fun fallsBackToRawOnTimeout() = runBlocking {
        val corrector = SttCorrector(
            client = FakeCorrectionClient("""{"corrected":"llama a juan"}""", delayMs = 2_000L),
            timeoutMs = 1_500L,
        )

        val result = corrector.correct(
            raw = "llama a juana",
            context = SttContext(knownContacts = listOf("Juan"), lastCommand = null),
        )

        assertEquals("llama a juana", result)
    }

    @Test
    fun fallsBackToRawOnJsonMalformed() = runBlocking {
        val corrector = SttCorrector(FakeCorrectionClient("sin JSON"))

        val result = corrector.correct(
            raw = "llama a juana",
            context = SttContext(knownContacts = listOf("Juan"), lastCommand = null),
        )

        assertEquals("llama a juana", result)
    }
}

private class FakeCorrectionClient(
    private val response: String,
    private val delayMs: Long = 0L,
) : TranscriptCorrectionClient {
    override suspend fun correctTranscript(prompt: String): String {
        if (delayMs > 0L) delay(delayMs)
        return response
    }
}
