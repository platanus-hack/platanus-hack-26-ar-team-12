package com.beto.app.voice

import com.beto.app.llm.Decision
import com.beto.app.llm.LlmClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseGeneratorTest {

    private class FakeLlmClient(
        private val response: String = "",
        private val delayMs: Long = 0,
        private val onCall: () -> Unit = {},
    ) : LlmClient {
        var calls = 0
            private set

        override suspend fun interpret(rawTranscript: String): Decision = Decision.Unknown

        override suspend fun generatePhrase(prompt: String): String {
            calls++
            onCall()
            if (delayMs > 0) delay(delayMs)
            return response
        }
    }

    @Test
    fun `returns LLM phrase when valid JSON and tone OK`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":"Le aviso a Juan, dale."}""")
        val gen = PhraseGenerator(llm)
        val out = gen.forIntent(PhraseIntent.CONFIRM_WHATSAPP, PhraseParams.forContact("Juan"))
        assertEquals("Le aviso a Juan, dale.", out)
    }

    @Test
    fun `cache hit on second call with same params skips LLM`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":"Le aviso a Juan, dale."}""")
        val gen = PhraseGenerator(llm)
        gen.forIntent(PhraseIntent.CONFIRM_WHATSAPP, PhraseParams.forContact("Juan"))
        gen.forIntent(PhraseIntent.CONFIRM_WHATSAPP, PhraseParams.forContact("Juan"))
        assertEquals(1, llm.calls)
    }

    @Test
    fun `different params miss cache`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":"Le aviso, dale."}""")
        val gen = PhraseGenerator(llm)
        gen.forIntent(PhraseIntent.CONFIRM_WHATSAPP, PhraseParams.forContact("Juan"))
        gen.forIntent(PhraseIntent.CONFIRM_WHATSAPP, PhraseParams.forContact("Pedro"))
        assertEquals(2, llm.calls)
    }

    @Test
    fun `falls back to PhraseFallbacks on LLM timeout`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":"too late"}""", delayMs = 2000)
        val gen = PhraseGenerator(llm, timeoutMs = 100)
        val out = gen.forIntent(PhraseIntent.UNKNOWN_COMMAND)
        assertEquals(PhraseFallbacks.forIntent(PhraseIntent.UNKNOWN_COMMAND, PhraseParams.empty()), out)
    }

    @Test
    fun `falls back when LLM returns malformed JSON`() = runBlocking {
        val llm = FakeLlmClient(response = "no es JSON")
        val gen = PhraseGenerator(llm)
        val out = gen.forIntent(PhraseIntent.UNKNOWN_COMMAND)
        assertEquals(PhraseFallbacks.forIntent(PhraseIntent.UNKNOWN_COMMAND, PhraseParams.empty()), out)
    }

    @Test
    fun `falls back when LLM returns empty phrase`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":""}""")
        val gen = PhraseGenerator(llm)
        val out = gen.forIntent(PhraseIntent.UNKNOWN_COMMAND)
        assertEquals(PhraseFallbacks.forIntent(PhraseIntent.UNKNOWN_COMMAND, PhraseParams.empty()), out)
    }

    @Test
    fun `falls back when phrase contains usted`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":"Le aviso a usted, señor."}""")
        val gen = PhraseGenerator(llm)
        val out = gen.forIntent(PhraseIntent.CONFIRM_WHATSAPP)
        // No es la respuesta del LLM, es el fallback
        assertNotEquals("Le aviso a usted, señor.", out)
        assertEquals(PhraseFallbacks.forIntent(PhraseIntent.CONFIRM_WHATSAPP, PhraseParams.empty()), out)
    }

    @Test
    fun `falls back when phrase has formal su pronoun`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":"Listo, su mensaje fue enviado."}""")
        val gen = PhraseGenerator(llm)
        val out = gen.forIntent(PhraseIntent.SUCCESS_WHATSAPP)
        assertEquals(PhraseFallbacks.forIntent(PhraseIntent.SUCCESS_WHATSAPP, PhraseParams.empty()), out)
    }

    @Test
    fun `falls back when phrase too long`() = runBlocking {
        val long = (1..30).joinToString(" ") { "palabra$it" }
        val llm = FakeLlmClient(response = """{"phrase":"$long"}""")
        val gen = PhraseGenerator(llm)
        val out = gen.forIntent(PhraseIntent.UNKNOWN_COMMAND)
        assertEquals(PhraseFallbacks.forIntent(PhraseIntent.UNKNOWN_COMMAND, PhraseParams.empty()), out)
    }

    @Test
    fun `falls back when phrase too short`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":"Listo"}""")
        val gen = PhraseGenerator(llm)
        val out = gen.forIntent(PhraseIntent.SUCCESS_CALL)
        assertEquals(PhraseFallbacks.forIntent(PhraseIntent.SUCCESS_CALL, PhraseParams.empty()), out)
    }

    @Test
    fun `falls back when phrase contains sanitization markers`() = runBlocking {
        val llm = FakeLlmClient(response = """{"phrase":"Te llamo al [TEL] ahora."}""")
        val gen = PhraseGenerator(llm)
        val out = gen.forIntent(PhraseIntent.CONFIRM_CALL)
        assertEquals(PhraseFallbacks.forIntent(PhraseIntent.CONFIRM_CALL, PhraseParams.empty()), out)
    }

    @Test
    fun `quality check accepts voseo phrases`() {
        listOf(
            "Le aviso a tu nieto, dale.",
            "Listo, te dejé el mensaje preparado.",
            "Llamando a Juan.",
        ).forEach {
            assertTrue("'$it' should pass quality check", PhraseGenerator.passesQualityChecks(it))
        }
    }

    @Test
    fun `quality check rejects formal phrases`() {
        listOf(
            "Le aviso a usted que ya se envió.",
            "Su mensaje ha sido procesado correctamente.",
            "Ustedes tienen que confirmar la operación.",
        ).forEach {
            assertFalse("'$it' should fail quality check", PhraseGenerator.passesQualityChecks(it))
        }
    }
}
