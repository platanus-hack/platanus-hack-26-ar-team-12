package com.beto.app.voice

/**
 * Frases hardcoded en es-AR (voseo, prohibido "usted") que se usan cuando:
 *  - La red está caída y no podemos llamar al LLM.
 *  - El LLM tarda más que el timeout de `PhraseGenerator` (800ms).
 *  - El LLM devuelve algo que no pasa los quality checks.
 *
 * Tono: cálido, breve, amistoso. Como hablaría un amigo argentino paciente.
 */
object PhraseFallbacks {

    fun forIntent(intent: PhraseIntent, params: PhraseParams): String = when (intent) {
        PhraseIntent.CONFIRM_WHATSAPP -> {
            val name = params.contactName ?: "tu contacto"
            "Le mando a $name por WhatsApp."
        }
        PhraseIntent.SUCCESS_WHATSAPP -> "Listo, te dejé el mensaje preparado."

        PhraseIntent.CONFIRM_CALL -> {
            val name = params.contactName ?: "tu contacto"
            "Llamo a $name."
        }
        PhraseIntent.SUCCESS_CALL -> "Listo, te abro la llamada."

        PhraseIntent.CONFIRM_SMS -> {
            val name = params.contactName ?: "tu contacto"
            "Le mando un mensaje a $name."
        }
        PhraseIntent.SUCCESS_SMS -> "Listo, te dejé el mensaje preparado."

        PhraseIntent.CONFIRM_MAPS -> "Te abro el mapa, dale."
        PhraseIntent.SUCCESS_MAPS -> "Listo, ahí lo tenés."

        PhraseIntent.FAILED_INTENT -> "No pude hacerlo. Probemos de nuevo en un ratito."
        PhraseIntent.UNKNOWN_COMMAND -> "No te entendí del todo, repetímelo más despacito."
    }
}
