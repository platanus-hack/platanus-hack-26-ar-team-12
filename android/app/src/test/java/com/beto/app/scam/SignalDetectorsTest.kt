package com.beto.app.scam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests por Signal individual. Cubre patrones positivos clásicos y un puñado de
 * controles negativos por señal para que la calibración no se degrade.
 */
class SignalDetectorsTest {

    private val engine = ScamRiskEngine()

    private fun signalsOf(text: String): List<Signal> = engine.assess(text).signals

    // ---------- URGENCY ----------

    @Test
    fun `URGENCY matches urgente urge apurate`() {
        listOf("Es urgente", "Urge resolverlo", "apurate por favor", "ya mismo dale", "ahora mismo")
            .forEach { assertHas(Signal.URGENCY, it) }
    }

    @Test
    fun `URGENCY matches antes del viernes`() {
        assertHas(Signal.URGENCY, "tenés que pagar antes del viernes")
        assertHas(Signal.URGENCY, "antes del cierre")
    }

    @Test
    fun `URGENCY does not fire on neutral mentions`() {
        assertMissing(Signal.URGENCY, "mañana paso a verte")
        assertMissing(Signal.URGENCY, "te llamo más tarde")
    }

    // ---------- MONEY_REQUEST ----------

    @Test
    fun `MONEY_REQUEST matches transferi cbu alias and amounts`() {
        listOf(
            "transferime",
            "transferir 80 mil",
            "pasame el cbu",
            "te paso el alias",
            "mercado pago",
            "\$5000",
            "5 lucas",
            "10 gambas",
        ).forEach { assertHas(Signal.MONEY_REQUEST, it) }
    }

    @Test
    fun `MONEY_REQUEST does not fire on neutral chat`() {
        // Trade-off conocido: el patrón "transfer[a-z]+" sí matchea "transferencia de archivos".
        // Aceptable en mensajería entre familia y adultos mayores. Verificamos solo que un saludo
        // genérico no dispare la señal por palabras sueltas.
        assertMissing(Signal.MONEY_REQUEST, "Hola, ¿cómo estás? Aprobé el examen, te cuento mañana.")
    }

    // ---------- CODE_REQUEST ----------

    @Test
    fun `CODE_REQUEST matches code asks`() {
        listOf(
            "pasame el código",
            "mandame el sms que te llega",
            "código de verificación",
            "decime el pin",
            "el código que me llega",
        ).forEach { assertHas(Signal.CODE_REQUEST, it) }
    }

    @Test
    fun `CODE_REQUEST does not fire on the word codigo alone`() {
        assertMissing(Signal.CODE_REQUEST, "el código postal es 1414")
        assertMissing(Signal.CODE_REQUEST, "código de área")
    }

    // ---------- NEW_NUMBER ----------

    @Test
    fun `NEW_NUMBER matches cambie de numero variants`() {
        listOf(
            "cambié de número",
            "cambié mi número",
            "este es mi nuevo número",
            "guardalo, mi número nuevo",
            "guardame este número nuevo",
        ).forEach { assertHas(Signal.NEW_NUMBER, it) }
    }

    @Test
    fun `NEW_NUMBER does not fire on the word numero alone`() {
        assertMissing(Signal.NEW_NUMBER, "el número del DNI es 12345")
        assertMissing(Signal.NEW_NUMBER, "qué número de calle es")
    }

    // ---------- SECRECY ----------

    @Test
    fun `SECRECY matches no le digas variants`() {
        listOf(
            "no le digas a papá",
            "no le cuentes a nadie",
            "no le avises todavía",
            "que no se entere mamá",
            "es un secreto",
        ).forEach { assertHas(Signal.SECRECY, it) }
    }

    // ---------- REMOTE_CONTROL ----------

    @Test
    fun `REMOTE_CONTROL matches anydesk teamviewer`() {
        listOf(
            "instalá AnyDesk",
            "abrí TeamViewer",
            "necesito control remoto",
            "instalá la app de soporte",
        ).forEach { assertHas(Signal.REMOTE_CONTROL, it) }
    }

    // ---------- SUSPICIOUS_LINK ----------

    @Test
    fun `SUSPICIOUS_LINK matches shorteners and plain http`() {
        listOf(
            "mirá: bit.ly/abc123",
            "tinyurl.com/xyz",
            "cutt.ly/asdf",
            "http://miweb-falsa.com/login",
        ).forEach { assertHas(Signal.SUSPICIOUS_LINK, it) }
    }

    @Test
    fun `SUSPICIOUS_LINK does not fire on plain https links`() {
        // https links a dominios reales no son señal por sí solos.
        assertMissing(Signal.SUSPICIOUS_LINK, "mirá la noticia en https://www.lanacion.com.ar/algo")
    }

    // ---------- IMPERSONATION_FAMILY ----------

    @Test
    fun `IMPERSONATION_FAMILY matches soy yo and soy tu nieto`() {
        listOf(
            "abu, soy yo",
            "soy tu nieto",
            "soy tu nieta",
            "no me reconocés?",
        ).forEach { assertHas(Signal.IMPERSONATION_FAMILY, it) }
    }

    // ---------- AUTHORITY_IMPERSONATION ----------

    @Test
    fun `AUTHORITY_IMPERSONATION matches AR organisms`() {
        listOf(
            "AFIP le informa",
            "ANSES: comunicado",
            "Banco Central comunica",
            "Tribunal Federal",
            "Policía Federal",
        ).forEach { assertHas(Signal.AUTHORITY_IMPERSONATION, it) }
    }

    // ---------- PRIZE_BAIT ----------

    @Test
    fun `PRIZE_BAIT matches prize patterns`() {
        listOf(
            "ganaste un viaje",
            "premio de 100 mil",
            "sorteo ganador",
            "ganador del sorteo",
            "lotería nacional",
        ).forEach { assertHas(Signal.PRIZE_BAIT, it) }
    }

    @Test
    fun `PRIZE_BAIT does not fire on the word premio alone`() {
        assertMissing(Signal.PRIZE_BAIT, "te lo digo como un premio simbólico")
    }

    // ---------- THREAT ----------

    @Test
    fun `THREAT matches embargo suspension denuncia`() {
        listOf(
            "evitar embargo",
            "su cuenta será suspendida",
            "suspensión de tu cuenta",
            "denuncia penal",
            "vas a perder la propiedad",
            "bloqueo de tu cuenta",
        ).forEach { assertHas(Signal.THREAT, it) }
    }

    // ---------- helpers ----------

    private fun assertHas(signal: Signal, text: String) {
        assertNotNull(
            "Signal $signal NO se detectó en: \"$text\"",
            engine.assess(text).hits.firstOrNull { it.signal == signal },
        )
    }

    private fun assertMissing(signal: Signal, text: String): SignalHit? {
        val hit = engine.assess(text).hits.firstOrNull { it.signal == signal }
        assertNull(
            "Signal $signal disparó por error en: \"$text\" (evidence=${hit?.evidence})",
            hit,
        )
        return hit
    }
}
