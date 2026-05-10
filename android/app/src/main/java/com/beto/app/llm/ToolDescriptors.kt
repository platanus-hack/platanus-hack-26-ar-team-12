package com.beto.app.llm

/**
 * Descriptores de las 5 tools que Phase 3 va a registrar en el LLM (vía Firebase AI Logic /
 * Gemini function calling).
 *
 * Phase 1 mergea estos STUBS COMENTADOS para reservar IDs y schemas. Phase 3 (LLM-02) los
 * activa registrando con el SDK de Firebase AI.
 *
 * Reglas (Pitfall #6):
 *  - Descripciones EN ESPAÑOL (mismo idioma que el system prompt — evita confusión bilingüe).
 *  - `required` explícito en cada parámetro.
 *  - Sin parámetros opcionales en Phase 3 (mejorar después).
 *  - Allow-list de nombres = exactamente estos 5.
 */
@Suppress("UnusedPrivateMember", "Unused")
object ToolDescriptors {

    // TODO Phase 3 (LLM-02): registrar con FunctionDeclaration de Firebase AI SDK.
    // Estructura tentativa:
    //
    // val SEND_WHATSAPP_DECL = FunctionDeclaration.newBuilder()
    //     .setName("send_whatsapp")
    //     .setDescription("Envía un mensaje de WhatsApp al contacto indicado. " +
    //                     "Usar SIEMPRE para WhatsApp en lugar del loop agéntico.")
    //     .addRequiredProperty("contact", "string", "Nombre del contacto (ej: 'mi nieto', 'Ana')")
    //     .addRequiredProperty("message", "string", "Texto del mensaje a enviar")
    //     .build()

    const val SEND_WHATSAPP = "send_whatsapp"
    const val MAKE_CALL = "make_call"
    const val SEND_SMS = "send_sms"
    const val OPEN_MAPS = "open_maps"
    const val AGENTIC_PERFORM_ACTION = "agentic_perform_action"

    val ALLOWED_TOOLS: Set<String> = setOf(
        SEND_WHATSAPP,
        MAKE_CALL,
        SEND_SMS,
        OPEN_MAPS,
    )

    val ALL_TOOL_NAMES: Set<String> = setOf(
        SEND_WHATSAPP,
        MAKE_CALL,
        SEND_SMS,
        OPEN_MAPS,
        AGENTIC_PERFORM_ACTION,
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
    )

    // TODO Phase 3: descripciones completas en español + ejemplos few-shot:
    //
    // make_call(contact: String) — "Llama por teléfono al contacto indicado. Ej: 'llamá a mi hijo'."
    // send_sms(contact: String, message: String) — "Envía un SMS al contacto. Ej: 'mandale un mensaje a Ana'."
    // open_maps(query: String) — "Abre Google Maps con la búsqueda. Ej: 'abrime el mapa hasta la farmacia'."
    // agentic_perform_action(goal: String) — "Ejecuta una acción genérica leyendo la pantalla.
    //                                          Usar SOLO cuando ningún otro tool aplica."
}

data class ToolDescriptor(
    val name: String,
    val description: String,
    val requiredArgs: List<String>,
)
