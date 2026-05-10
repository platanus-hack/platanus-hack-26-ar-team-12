package com.beto.app.voice

/**
 * Intents semánticos para los que `PhraseGenerator` produce frases TTS.
 *
 * Cada intent corresponde a un momento del flujo de Beto donde queremos voz cálida y
 * natural en es-AR (voseo, prohibido "usted"). El `PhraseGenerator` usa el LLM para
 * generar variaciones contextuales y cae a `PhraseFallbacks` si la red falla.
 */
enum class PhraseIntent {
    /** Confirma antes de mandar WhatsApp ("Le aviso a tu nieto que ya llegaste, dale."). */
    CONFIRM_WHATSAPP,
    /** Reporta éxito tras lanzar WhatsApp ("Listo, te dejé el mensaje preparado."). */
    SUCCESS_WHATSAPP,

    /** Confirma antes de llamar ("Llamo a tu hijo, dale."). */
    CONFIRM_CALL,
    /** Reporta éxito tras lanzar dialer ("Listo, te abro la llamada."). */
    SUCCESS_CALL,

    /** Confirma antes de mandar SMS. */
    CONFIRM_SMS,
    /** Reporta éxito tras abrir composer SMS. */
    SUCCESS_SMS,

    /** Confirma antes de abrir Maps. */
    CONFIRM_MAPS,
    /** Reporta éxito tras abrir Maps. */
    SUCCESS_MAPS,

    /** Falla genérica de un Intent (app no instalada, etc.). */
    FAILED_INTENT,

    /** No entendió el comando. */
    UNKNOWN_COMMAND,
}

/**
 * Parámetros opcionales para personalizar la frase generada.
 * `canonical()` devuelve una representación estable usada como key del cache.
 */
data class PhraseParams(
    val contactName: String? = null,
    val message: String? = null,
    val mapsQuery: String? = null,
) {
    fun canonical(): String = listOfNotNull(
        contactName?.let { "contact=$it" },
        message?.let { "msg=$it" },
        mapsQuery?.let { "q=$it" },
    ).joinToString("|").ifEmpty { "noargs" }

    companion object {
        fun empty(): PhraseParams = PhraseParams()
        fun forContact(name: String): PhraseParams = PhraseParams(contactName = name)
        fun forMessage(name: String, message: String): PhraseParams =
            PhraseParams(contactName = name, message = message)
        fun forMaps(query: String): PhraseParams = PhraseParams(mapsQuery = query)
    }
}
