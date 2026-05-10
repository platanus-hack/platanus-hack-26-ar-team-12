package com.beto.app.scam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del orquestador. Cubre:
 *  - Whitelist de packages
 *  - Threshold (solo HIGH dispara por default)
 *  - Throttle de assessments
 *  - Cooldown post-emit
 *  - Dedupe por hash del contenido
 *  - Buffer concatenando fragmentos partidos en burbujas
 */
class ScamWatcherTest {

    private val pkg = ScamPackages.WHATSAPP
    private val nonWatched = "com.example.tetris"

    @Test
    fun `non-whitelisted package is ignored`() {
        val w = ScamWatcher()
        val d = w.observe(nonWatched, PITCH_TEXT, nowMs = 0L)
        assertEquals(ScamWatcher.Decision.Ignored, d)
    }

    @Test
    fun `single fragment with full pitch text emits HIGH`() {
        val w = ScamWatcher()
        val d = w.observe(pkg, PITCH_TEXT, nowMs = 0L)
        assertTrue("expected Emit, got $d", d is ScamWatcher.Decision.Emit)
        d as ScamWatcher.Decision.Emit
        assertEquals(RiskLevel.HIGH, d.assessment.level)
        assertEquals(pkg, d.packageName)
        assertTrue(d.contextHash.isNotBlank())
    }

    @Test
    fun `signals partidos en dos fragmentos cruzan via buffer y emiten`() {
        val w = ScamWatcher(minIntervalBetweenAssessmentsMs = 0L)
        val d1 = w.observe(pkg, "Hola abu, soy yo. Cambié de número, guardalo.", nowMs = 0L)
        // Una sola burbuja con varias señales puede ya dar HIGH; aceptamos cualquier resultado no-Emit
        // o Emit: lo crítico es que la SEGUNDA mensaje complete el pattern si faltaba.
        val d2 = w.observe(pkg, "¿me podés transferir 80 mil ahora? Es urgente, no le digas a nadie.", nowMs = 100L)
        val emitted = listOf(d1, d2).any { it is ScamWatcher.Decision.Emit }
        assertTrue("alguno de los dos fragmentos debe terminar emitiendo HIGH", emitted)
    }

    @Test
    fun `legitimate message stays below threshold`() {
        val w = ScamWatcher()
        val d = w.observe(pkg, "Hola abu, te confirmo el turno del médico para el martes a las 10.", nowMs = 0L)
        assertTrue("expected BelowThreshold, got $d", d is ScamWatcher.Decision.BelowThreshold)
    }

    @Test
    fun `throttle blocks rapid back-to-back observations`() {
        val w = ScamWatcher(minIntervalBetweenAssessmentsMs = 500L)
        val d1 = w.observe(pkg, "Hola", nowMs = 1000L)
        val d2 = w.observe(pkg, "Cómo va", nowMs = 1100L) // dentro de 500ms
        assertTrue(d1 is ScamWatcher.Decision.BelowThreshold)
        assertEquals(ScamWatcher.Decision.Throttled, d2)
    }

    @Test
    fun `throttle resets after the window`() {
        val w = ScamWatcher(minIntervalBetweenAssessmentsMs = 500L)
        w.observe(pkg, "Hola", nowMs = 1000L)
        val d = w.observe(pkg, "Cómo va todo", nowMs = 1700L) // > 500ms después
        assertFalse("debería NO estar throttled, fue: $d", d == ScamWatcher.Decision.Throttled)
    }

    @Test
    fun `cooldown blocks new emits on same package after HIGH`() {
        val w = ScamWatcher(
            cooldownAfterEmitMs = 60_000L,
            minIntervalBetweenAssessmentsMs = 0L,
        )
        val first = w.observe(pkg, PITCH_TEXT, nowMs = 0L)
        assertTrue(first is ScamWatcher.Decision.Emit)

        // Otro mensaje fraudulento DISTINTO durante cooldown → no re-emite
        val during = w.observe(pkg, "AFIP multa urgente, transferí YA al CBU 123, no le digas a nadie", nowMs = 1_000L)
        assertEquals(ScamWatcher.Decision.Cooldown, during)
    }

    @Test
    fun `cooldown is per package`() {
        val w = ScamWatcher(
            cooldownAfterEmitMs = 60_000L,
            minIntervalBetweenAssessmentsMs = 0L,
        )
        val first = w.observe(pkg, PITCH_TEXT, nowMs = 0L)
        assertTrue(first is ScamWatcher.Decision.Emit)

        val sms = w.observe(
            ScamPackages.GOOGLE_MESSAGES,
            "AFIP: tiene una multa pendiente, transferí ahora al CBU 0123, urgente, no le diga a nadie",
            nowMs = 100L,
        )
        assertTrue("otro pkg en cooldown ajeno debería poder emitir, got $sms", sms is ScamWatcher.Decision.Emit)
    }

    @Test
    fun `dedupe ignores second emit attempt with identical content`() {
        // Cooldown corto pero hash idéntico → debe caer en Deduped si limpiamos cooldown
        val w = ScamWatcher(
            cooldownAfterEmitMs = 0L,           // sin cooldown
            minIntervalBetweenAssessmentsMs = 0L, // sin throttle
        )
        val d1 = w.observe(pkg, PITCH_TEXT, nowMs = 0L)
        assertTrue(d1 is ScamWatcher.Decision.Emit)

        // mismo texto exacto → buffer dedupe trivial Y mismo hash
        val d2 = w.observe(pkg, PITCH_TEXT, nowMs = 1L)
        assertEquals(ScamWatcher.Decision.Deduped, d2)
    }

    @Test
    fun `reset clears state for one package`() {
        val w = ScamWatcher(
            cooldownAfterEmitMs = 60_000L,
            minIntervalBetweenAssessmentsMs = 0L,
        )
        w.observe(pkg, PITCH_TEXT, nowMs = 0L)
        w.reset(pkg)
        // sin reset estaríamos en cooldown; con reset se debe poder evaluar de nuevo
        val d = w.observe(pkg, "Hola abu, todo bien?", nowMs = 100L)
        assertNotEquals(ScamWatcher.Decision.Cooldown, d)
    }

    @Test
    fun `emit carries assessment hits and original window text`() {
        val w = ScamWatcher()
        val d = w.observe(pkg, PITCH_TEXT, nowMs = 0L) as ScamWatcher.Decision.Emit
        assertTrue(d.assessment.hits.isNotEmpty())
        assertTrue(d.text.contains("transfer", ignoreCase = true))
        assertTrue(d.text.contains("urgente", ignoreCase = true))
    }

    @Test
    fun `blank text is ignored even on watched package`() {
        val w = ScamWatcher()
        val d = w.observe(pkg, "   ", nowMs = 0L)
        assertEquals(ScamWatcher.Decision.Ignored, d)
    }

    private companion object {
        const val PITCH_TEXT =
            "Hola abu, soy yo. Cambié de número, guardalo. Estoy en un quilombo, " +
                "¿me podés transferir 80 mil ahora? Es urgente. No le digas a papá todavía"
    }
}
