package com.beto.app.llm

/**
 * Descriptores de los tools que el LLM (Gemini vía Firebase AI Logic) puede invocar.
 *
 * Reglas:
 *  - Descripciones EN ESPAÑOL (mismo idioma que el system prompt).
 *  - `requiredArgs` explícito en cada descriptor.
 *  - `ALLOWED_TOOLS` es la allow-list que valida `DecisionJson.decode`.
 */
object ToolDescriptors {

    const val SEND_WHATSAPP = "send_whatsapp"
    const val MAKE_CALL = "make_call"
    const val SEND_SMS = "send_sms"
    const val OPEN_MAPS = "open_maps"
    const val SHOW_HOW_TO = "show_how_to"

    val ALLOWED_TOOLS: Set<String> = setOf(
        SEND_WHATSAPP,
        MAKE_CALL,
        SEND_SMS,
        OPEN_MAPS,
        SHOW_HOW_TO,
    )

    val DESCRIPTORS: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = SEND_WHATSAPP,
            description = "Envía un mensaje de WhatsApp al contacto indicado.",
            requiredArgs = listOf("contact", "message"),
        ),
        ToolDescriptor(
            name = MAKE_CALL,
            description = "Llama por teléfono al contacto indicado.",
            requiredArgs = listOf("contact"),
        ),
        ToolDescriptor(
            name = SEND_SMS,
            description = "Envía un SMS al contacto indicado.",
            requiredArgs = listOf("contact", "message"),
        ),
        ToolDescriptor(
            name = OPEN_MAPS,
            description = "Abre Google Maps con una búsqueda o destino.",
            requiredArgs = listOf("query"),
        ),
        ToolDescriptor(
            name = SHOW_HOW_TO,
            description = "Cuando el usuario pregunta cómo usar el celular (ej. '¿cómo mando un audio?', " +
                "'¿cómo hago una videollamada?', '¿cómo agrego un contacto?', '¿cómo subo el volumen?', " +
                "'¿cómo abro la cámara?'), muestra una guía visual con flecha animada sobre el botón " +
                "correcto y explica paso a paso. Acciones permitidas: send_whatsapp_audio, make_video_call, " +
                "add_contact, increase_volume, open_camera.",
            requiredArgs = listOf("action"),
        ),
    )
}

data class ToolDescriptor(
    val name: String,
    val description: String,
    val requiredArgs: List<String>,
)
