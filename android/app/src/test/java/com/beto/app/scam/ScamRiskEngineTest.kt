package com.beto.app.scam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del cerebro local. El demo case del pitch es la verdad: si este test rompe,
 * el corazón del producto rompe.
 */
class ScamRiskEngineTest {

    private val engine = ScamRiskEngine()

    // ---------- Demo case del pitch (slide 4) ----------

    @Test
    fun `demo case from pitch returns HIGH with at least 3 signals`() {
        val pitchChat = """
            Hola abu, soy yo. Cambié de número, guardalo.
            Estoy en un quilombo, ¿me podés transferir 80 mil ahora? Después te explico.
            Es urgente. No le digas a papá todavía 🙏
        """.trimIndent()

        val result = engine.assess(pitchChat)

        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(
            "Expected at least 3 signals from the pitch demo, got ${result.signals}",
            result.signals.size >= 3,
        )
        assertTrue(result.shouldAlertProactively)
    }

    @Test
    fun `demo case detects the four headline signals from pitch slide 4`() {
        // Pitch slide 4 muestra estos chips: urgencia · pedido de dinero · cambié de número · no le digas a nadie
        val pitchChat = """
            Hola abu, soy yo. Cambié de número, guardalo.
            ¿me podés transferir 80 mil ahora? Es urgente. No le digas a papá todavía
        """.trimIndent()

        val signals = engine.assess(pitchChat).signals

        assertTrue("missing URGENCY in $signals", Signal.URGENCY in signals)
        assertTrue("missing MONEY_REQUEST in $signals", Signal.MONEY_REQUEST in signals)
        assertTrue("missing NEW_NUMBER in $signals", Signal.NEW_NUMBER in signals)
        assertTrue("missing SECRECY in $signals", Signal.SECRECY in signals)
    }

    // ---------- Threshold (regla del pitch slide 7: 1=ruido, 3=patrón) ----------

    @Test
    fun `zero signals returns NONE`() {
        val result = engine.assess("Hola abu, mañana paso a verte a las 5. Te quiero.")
        assertEquals(RiskLevel.NONE, result.level)
        assertTrue(result.hits.isEmpty())
        assertFalse(result.shouldAlertProactively)
    }

    @Test
    fun `one signal returns LOW`() {
        val result = engine.assess("Llamame ahora mismo")
        assertEquals(RiskLevel.LOW, result.level)
        assertEquals(1, result.signals.size)
        assertFalse("LOW debe NO disparar overlay proactivo", result.shouldAlertProactively)
    }

    @Test
    fun `two signals returns MEDIUM`() {
        val result = engine.assess("Necesito plata, es urgente")
        assertEquals(RiskLevel.MEDIUM, result.level)
        assertEquals(2, result.signals.size)
        assertFalse("MEDIUM debe NO disparar overlay proactivo en v1", result.shouldAlertProactively)
    }

    @Test
    fun `three signals returns HIGH`() {
        val result = engine.assess("Cambié de número, mandame plata urgente")
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(result.shouldAlertProactively)
    }

    // ---------- Control negativo: mensajes legítimos NO deben disparar ----------

    @Test
    fun `legitimate doctor reminder returns NONE`() {
        val result = engine.assess(
            "Hola, te confirmo el turno con la Dra. Pérez el viernes 14 a las 10:30. " +
                "Si no podés, llamá al 11-4567-8900.",
        )
        assertEquals(RiskLevel.NONE, result.level)
    }

    @Test
    fun `legitimate family message returns NONE`() {
        val result = engine.assess("Abu, te quiero. Mañana te llevo facturas.")
        assertEquals(RiskLevel.NONE, result.level)
    }

    @Test
    fun `legitimate bank notification returns NONE`() {
        val result = engine.assess("Galicia: tu tarjeta fue activada. Ya podés usarla con normalidad.")
        assertEquals(RiskLevel.NONE, result.level)
    }

    @Test
    fun `link alone is LOW not HIGH`() {
        // Una señal sola no dispara: pitch slide 7 — "una señal sola es ruido".
        val result = engine.assess("Mirá esto: http://example.com/algo")
        assertEquals(RiskLevel.LOW, result.level)
        assertFalse(result.shouldAlertProactively)
    }

    // ---------- Casos AR conocidos ----------

    @Test
    fun `fake AFIP SMS returns HIGH`() {
        val result = engine.assess(
            "AFIP: tiene una multa pendiente. Pagar antes del viernes para evitar embargo. CBU 0123-4567",
        )
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(Signal.AUTHORITY_IMPERSONATION in result.signals)
        assertTrue(Signal.URGENCY in result.signals)
        assertTrue(Signal.THREAT in result.signals)
        assertTrue(Signal.MONEY_REQUEST in result.signals)
    }

    @Test
    fun `fake prize with shortener returns HIGH`() {
        val result = engine.assess(
            "Felicitaciones, ganaste un premio de 500 mil pesos. Reclamalo ya mismo en bit.ly/premio-arg",
        )
        assertTrue(
            "expected HIGH, got ${result.level} with ${result.signals}",
            result.level == RiskLevel.HIGH,
        )
        assertTrue(Signal.PRIZE_BAIT in result.signals)
        assertTrue(Signal.SUSPICIOUS_LINK in result.signals)
        assertTrue(Signal.URGENCY in result.signals)
    }

    @Test
    fun `remote support scam returns HIGH`() {
        val result = engine.assess(
            "Soy del banco, instalá AnyDesk para resolverlo. Pasame el código que te llega ahora mismo.",
        )
        assertEquals(RiskLevel.HIGH, result.level)
        assertTrue(Signal.REMOTE_CONTROL in result.signals)
        assertTrue(Signal.CODE_REQUEST in result.signals)
        assertTrue(Signal.URGENCY in result.signals)
    }

    // ---------- Edge cases ----------

    @Test
    fun `empty input returns NONE`() {
        assertEquals(RiskLevel.NONE, engine.assess("").level)
        assertEquals(RiskLevel.NONE, engine.assess("   \n\t").level)
    }

    @Test
    fun `case and diacritics insensitive`() {
        val withTildes = engine.assess("CAMBIÉ DE NÚMERO. Es URGENTE. Mandame PLATA.")
        assertEquals(RiskLevel.HIGH, withTildes.level)
    }

    @Test
    fun `each hit carries non-empty evidence`() {
        val result = engine.assess(
            "Hola abu, soy yo. Cambié de número. Mandame 80 mil urgente. No le digas a nadie.",
        )
        assertTrue(result.hits.isNotEmpty())
        result.hits.forEach { hit ->
            assertTrue("evidence vacía para ${hit.signal}", hit.evidence.isNotBlank())
        }
    }

    @Test
    fun `analyzedLength matches input length`() {
        val text = "mensaje de prueba"
        assertEquals(text.length, engine.assess(text).analyzedLength)
    }

    @Test
    fun `same signal cannot count twice from one text`() {
        // Si "urgente" y "ya mismo" aparecen juntos, ambos son URGENCY → cuentan 1, no 2.
        val result = engine.assess("Es urgente, ya mismo")
        assertEquals(RiskLevel.LOW, result.level)
        assertEquals(1, result.signals.size)
        assertEquals(Signal.URGENCY, result.signals.first())
    }

    @Test
    fun `injection of custom detectors works`() {
        // Smoke test del DI: el engine acepta detectors custom (útil para Block 5).
        val custom = ScamRiskEngine(detectors = emptyList())
        assertEquals(RiskLevel.NONE, custom.assess("cambié de número, mandame plata urgente").level)
    }
}
