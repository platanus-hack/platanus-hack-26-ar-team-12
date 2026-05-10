package com.beto.app.llm

object PromptBuilder {
    val fewShots: List<Decision> = listOf(
        Decision.ToolCall(
            tool = ToolDescriptors.SEND_WHATSAPP,
            args = mapOf("contact" to "Juan", "message" to "ya llegué"),
        ),
        Decision.ToolCall(
            tool = ToolDescriptors.SEND_WHATSAPP,
            args = mapOf("contact" to "mi nieto", "message" to "paso a buscarte"),
        ),
        Decision.ToolCall(
            tool = ToolDescriptors.SEND_SMS,
            args = mapOf("contact" to "Ana", "message" to "estoy en camino"),
        ),
        Decision.ToolCall(
            tool = ToolDescriptors.MAKE_CALL,
            args = mapOf("contact" to "Carlos"),
        ),
        Decision.ToolCall(
            tool = ToolDescriptors.OPEN_MAPS,
            args = mapOf("query" to "farmacia cerca"),
        ),
        Decision.NeedsClarification(
            question = "¿Quién es tu nieto?",
            expecting = ExpectedAnswer.CONTACT_NAME,
        ),
        Decision.NeedsClarification(
            question = "¿Por WhatsApp, SMS o llamada?",
            expecting = ExpectedAnswer.CHANNEL,
        ),
        Decision.Unknown,
    )

    fun buildInterpretPrompt(sanitizedTranscript: String): String = buildString {
        appendLine(systemPrompt())
        appendLine()
        appendLine("Tools disponibles:")
        ToolDescriptors.DESCRIPTORS.forEach { descriptor ->
            appendLine("- ${descriptor.name}(${descriptor.requiredArgs.joinToString(", ")}): ${descriptor.description}")
        }
        appendLine()
        appendLine("Schema de respuesta JSON estricto:")
        appendLine("""{"type":"tool_call","tool":"send_whatsapp","args":{"contact":"Juan","message":"hola"}}""")
        appendLine("""{"type":"needs_clarification","question":"¿Quién es tu nieto?","expecting":"CONTACT_NAME"}""")
        appendLine("""{"type":"unknown"}""")
        appendLine()
        appendLine("Few-shots:")
        appendLine("Usuario: mandale a Juan que ya llegué")
        appendLine("Beto: ${DecisionJson.encode(fewShots[0])}")
        appendLine("Usuario: mandale a mi nieto que paso a buscarte")
        appendLine("Beto: ${DecisionJson.encode(fewShots[1])}")
        appendLine("Usuario: mandale SMS a Ana que estoy en camino")
        appendLine("Beto: ${DecisionJson.encode(fewShots[2])}")
        appendLine("Usuario: llamá a Carlos")
        appendLine("Beto: ${DecisionJson.encode(fewShots[3])}")
        appendLine("Usuario: abrime el mapa hasta la farmacia cerca")
        appendLine("Beto: ${DecisionJson.encode(fewShots[4])}")
        appendLine("Usuario: llamá a mi nieto")
        appendLine("Beto: ${DecisionJson.encode(fewShots[5])}")
        appendLine("Usuario: mandale a Juan que ya salgo")
        appendLine("Beto: ${DecisionJson.encode(fewShots[6])}")
        appendLine("Usuario: contame un chiste")
        appendLine("Beto: ${DecisionJson.encode(fewShots[7])}")
        appendLine()
        appendLine("Usuario: $sanitizedTranscript")
        appendLine("Beto:")
    }

    fun systemPrompt(): String =
        """
        Sos Beto, un asistente cálido para adultos mayores en Argentina.
        Tu trabajo es interpretar lo que dijo el usuario y devolver una decisión estructurada.
        Usá vocabulario simple, voseo argentino y frases cortas.
        No ejecutes acciones por texto libre: devolvé una tool permitida, una clarification o unknown.
        Si falta el contacto, devolvé needs_clarification con expecting CONTACT_NAME.
        Si falta el canal para un mensaje, devolvé needs_clarification con expecting CHANNEL.
        Si no estás seguro, devolvé unknown para que el matcher determinista tome la posta.
        Respondé solamente JSON válido, sin Markdown ni explicación.
        """.trimIndent()

    /**
     * Construye un prompt que pide al LLM una frase TTS corta para el `intent` dado.
     * Output schema esperado: `{"phrase":"..."}`.
     *
     * Tono enforced en system + few-shots: voseo argentino, prohibido "usted",
     * 1-2 oraciones máximas, palabras simples, tono de amigo.
     */
    fun buildPhraseGenPrompt(
        intent: com.beto.app.voice.PhraseIntent,
        params: com.beto.app.voice.PhraseParams,
    ): String = buildString {
        appendLine(phraseSystemPrompt())
        appendLine()
        appendLine("Intent: $intent")
        appendLine("Contexto:")
        params.contactName?.let { appendLine("  contact=$it") }
        params.message?.let { appendLine("  message=$it") }
        params.mapsQuery?.let { appendLine("  query=$it") }
        appendLine()
        appendLine("Ejemplos (todos en voseo, sin 'usted', cortos):")
        appendLine("""intent=CONFIRM_WHATSAPP contact=Juan -> {"phrase":"Le aviso a Juan por WhatsApp, dale."}""")
        appendLine("""intent=SUCCESS_WHATSAPP -> {"phrase":"Listo, te dejé el mensaje preparado."}""")
        appendLine("""intent=CONFIRM_CALL contact=mi hijo -> {"phrase":"Llamo a tu hijo."}""")
        appendLine("""intent=SUCCESS_CALL -> {"phrase":"Listo, te abro la llamada."}""")
        appendLine("""intent=CONFIRM_MAPS query=farmacia -> {"phrase":"Te busco la farmacia, dale."}""")
        appendLine("""intent=FAILED_INTENT -> {"phrase":"Uy, no me salió. Probemos de nuevo en un ratito."}""")
        appendLine("""intent=UNKNOWN_COMMAND -> {"phrase":"No te entendí del todo, repetímelo más despacito."}""")
        appendLine()
        appendLine("Generá ahora la frase para el intent indicado. Solo JSON válido:")
    }

    private fun phraseSystemPrompt(): String =
        """
        Sos Beto, un amigo argentino paciente que habla con un adulto mayor.
        Hablás SIEMPRE en voseo argentino: "tenés", "decime", "mirá", "dale", "tranquilo".
        NUNCA decís "usted", "ustedes", ni "su" en sentido formal.
        Tono: cálido, simple, breve. Una o dos oraciones MÁXIMO.
        Podés decir "che", "dale", "tranquilo" cuando suene natural.
        Evitá tecnicismos. No expliques nada de más.
        Devolvé solo JSON válido con schema {"phrase":"..."}, sin Markdown.
        """.trimIndent()
}
