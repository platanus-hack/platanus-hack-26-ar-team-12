package com.beto.app.scam

import com.beto.app.llm.Decision
import com.beto.app.llm.LlmClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScamExplainerTest {

    private val pitchAssessment: RiskAssessment by lazy {
        ScamRiskEngine().assess(PITCH_TEXT)
    }

    @Test
    fun `prompt includes detected signals and sanitized text`() {
        val explainer = ScamExplainer(StubLlm("""{"phrase":"x"}"""))
        val prompt = explainer.buildPrompt(pitchAssessment, "transferí al CBU 0123456", "tu nieto Fran")
        // signals presentes
        assertTrue("debe listar 'pedido de dinero'", prompt.contains("pedido de dinero"))
        assertTrue("debe listar 'urgencia'", prompt.contains("urgencia"))
        // contacto de confianza presente
        assertTrue("debe mencionar trusted contact", prompt.contains("tu nieto Fran"))
        // sanitización aplicada (DNI/teléfono/CBU placeholders) — el caller pasa raw,
        // explainer normaliza. Acá usamos texto sin DNI/tel para no requerir setup.
    }

    @Test
    fun `prompt notes missing trusted contact when none provided`() {
        val explainer = ScamExplainer(StubLlm("""{"phrase":"x"}"""))
        val prompt = explainer.buildPrompt(pitchAssessment, "hola", null)
        assertTrue(prompt.contains("No hay contacto de confianza"))
    }

    @Test
    fun `explain returns parsed phrase when LLM responds with valid JSON`() = runTest {
        val explainer = ScamExplainer(
            llm = StubLlm("""{"phrase":"Mejor frenemos, llamemos a tu nieto Fran antes de seguir."}"""),
        )
        val result = explainer.explain(pitchAssessment, PITCH_TEXT, "tu nieto Fran")
        assertEquals("Mejor frenemos, llamemos a tu nieto Fran antes de seguir.", result)
    }

    @Test
    fun `explain tolerates markdown fences around JSON`() = runTest {
        val explainer = ScamExplainer(
            llm = StubLlm(
                """
                ```json
                {"phrase":"Te están apurando con plata, llamemos a alguien de confianza primero."}
                ```
                """.trimIndent(),
            ),
        )
        val result = explainer.explain(pitchAssessment, PITCH_TEXT, null)
        assertNotNull(result)
        assertTrue(result!!.contains("alguien de confianza"))
    }

    @Test
    fun `explain returns null when LLM returns invalid JSON`() = runTest {
        val explainer = ScamExplainer(StubLlm("not json at all"))
        assertNull(explainer.explain(pitchAssessment, PITCH_TEXT, null))
    }

    @Test
    fun `explain returns null when LLM returns empty string`() = runTest {
        val explainer = ScamExplainer(StubLlm(""))
        assertNull(explainer.explain(pitchAssessment, PITCH_TEXT, null))
    }

    @Test
    fun `explain returns null when phrase is too short to be useful`() = runTest {
        val explainer = ScamExplainer(StubLlm("""{"phrase":"hola"}"""))
        assertNull(explainer.explain(pitchAssessment, PITCH_TEXT, null))
    }

    @Test
    fun `explain returns null when phrase is suspiciously long`() = runTest {
        val long = "x".repeat(400)
        val explainer = ScamExplainer(StubLlm("""{"phrase":"$long"}"""))
        assertNull(explainer.explain(pitchAssessment, PITCH_TEXT, null))
    }

    @Test
    fun `explain returns null when assessment has no signals`() = runTest {
        val empty = RiskAssessment.empty()
        val explainer = ScamExplainer(StubLlm("""{"phrase":"esto no debería disparar"}"""))
        assertNull(explainer.explain(empty, "texto neutro", null))
    }

    @Test
    fun `explain falls back to null on timeout`() = runTest {
        val slowLlm = object : LlmClient {
            override suspend fun interpret(rawTranscript: String): Decision = Decision.Unknown
            override suspend fun generatePhrase(prompt: String): String {
                delay(5_000)
                return """{"phrase":"too late"}"""
            }
        }
        // Timeout chico para que el test sea rápido y determinista.
        val explainer = ScamExplainer(slowLlm, timeoutMs = 50L)
        assertNull(explainer.explain(pitchAssessment, PITCH_TEXT, null))
    }

    @Test
    fun `explain falls back to null when LLM throws`() = runTest {
        val throwingLlm = object : LlmClient {
            override suspend fun interpret(rawTranscript: String): Decision = Decision.Unknown
            override suspend fun generatePhrase(prompt: String): String {
                throw RuntimeException("network down")
            }
        }
        val explainer = ScamExplainer(throwingLlm)
        assertNull(explainer.explain(pitchAssessment, PITCH_TEXT, null))
    }

    @Test
    fun `prompt sanitizes phone numbers and DNIs in raw text`() {
        val explainer = ScamExplainer(StubLlm("""{"phrase":"x"}"""))
        val raw = "transferí al 1130405060 desde el DNI 30123456"
        val prompt = explainer.buildPrompt(pitchAssessment, com.beto.app.llm.Sanitizer.sanitize(raw), null)
        assertTrue("DNI debe estar tachado", prompt.contains("[DNI]"))
        assertTrue("teléfono debe estar tachado", prompt.contains("[TEL]"))
    }

    private class StubLlm(private val response: String) : LlmClient {
        override suspend fun interpret(rawTranscript: String): Decision = Decision.Unknown
        override suspend fun generatePhrase(prompt: String): String = response
    }

    private companion object {
        const val PITCH_TEXT =
            "Hola abu, soy yo. Cambié de número, guardalo. Estoy en un quilombo, " +
                "¿me podés transferir 80 mil ahora? Es urgente. No le digas a papá todavía"
    }
}
