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
            tool = ToolDescriptors.MAKE_CALL,
            args = mapOf("contact" to "Fran Iturain"),
        ),
        Decision.ToolCall(
            tool = ToolDescriptors.SEND_WHATSAPP,
            args = mapOf("contact" to "María José", "message" to "salgo en 5"),
        ),
        Decision.ToolCall(
            tool = ToolDescriptors.OPEN_MAPS,
            args = mapOf("query" to "farmacia cerca"),
        ),
        Decision.ToolCall(
            tool = ToolDescriptors.SHOW_HOW_TO,
            args = mapOf("action" to "send_whatsapp_audio"),
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
        // Few-shots críticos: nombre+apellido propio NO debe gatillar clarificación.
        // Antes de agregar estos, el modelo respondía "¿quién es tu Fran Iturain?"
        // como si el apellido fuera un alias relacional ("mi primo", "mi vecino").
        appendLine("Usuario: llamá a Fran Iturain")
        appendLine("Beto: ${DecisionJson.encode(fewShots[4])}")
        appendLine("Usuario: mandale a María José que salgo en 5")
        appendLine("Beto: ${DecisionJson.encode(fewShots[5])}")
        appendLine("Usuario: abrime el mapa hasta la farmacia cerca")
        appendLine("Beto: ${DecisionJson.encode(fewShots[6])}")
        appendLine("Usuario: ¿cómo mando un audio por WhatsApp?")
        appendLine("Beto: ${DecisionJson.encode(fewShots[7])}")
        appendLine("Usuario: llamá a mi nieto")
        appendLine("Beto: ${DecisionJson.encode(fewShots[8])}")
        appendLine("Usuario: mandale a Juan que ya salgo")
        appendLine("Beto: ${DecisionJson.encode(fewShots[9])}")
        appendLine("Usuario: contame un chiste")
        appendLine("Beto: ${DecisionJson.encode(fewShots[10])}")
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

        IMPORTANTE sobre contactos:
        - Si el usuario nombra a alguien con un nombre propio (sea solo nombre como "Juan",
          o nombre y apellido como "Fran Iturain", "María José", "Ana Martínez"), tratalo
          como `contact` y devolvé la tool DIRECTAMENTE. NO clarifiques.
        - Solo devolvé `needs_clarification` con `CONTACT_NAME` si el usuario usa una
          referencia relacional ambigua sin nombre ("a mi primo", "a la doctora", "a él")
          y no sabés todavía a quién se refiere.
        - Aliases familiares como "mi nieto Juan" o "mi hija Sofi" SÍ traen el nombre,
          tratalos como tool_call con contact="mi nieto Juan" o contact="Sofi".

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

    /**
     * Prompt del Modo Compañero (chat conversacional). Distinto al Motor de Acciones —
     * este es para CHARLAR, no para ejecutar tools. System prompt explícito de "no ofrezcas
     * hacer tareas" para que no intente responder con tool calls.
     */
    fun buildCompanionChat(history: List<com.beto.app.companion.CompanionMessage>): String = buildString {
        appendLine(companionSystemPrompt())
        appendLine()
        appendLine("Conversación hasta ahora:")
        history.takeLast(10).forEach { msg ->
            val speaker = when (msg.role) {
                com.beto.app.companion.Role.USER -> "Usuario"
                com.beto.app.companion.Role.BETO -> "Beto"
            }
            appendLine("$speaker: ${msg.text}")
        }
        appendLine("Beto:")
    }

    private fun companionSystemPrompt(): String =
        """
        Sos Beto. Estás charlando con un adulto mayor en Argentina.
        Vocabulario simple. Voseo argentino siempre. NUNCA "usted".
        Respondé en una o dos oraciones máximo. Sé cálido, paciente y curioso.
        NO ofrezcas hacer tareas — esto es solo charlar. Si te piden hacer algo
        (mandar mensajes, llamar, etc.), decí algo cálido como
        "Cuando quieras, cerrá la charla y tocame en la burbuja para eso".
        Si el usuario te cuenta algo personal (gustos, familia, ciudad, mascota,
        cumpleaños), interesate sin ser invasivo.
        Respondé solo el texto de Beto, sin prefijos como "Beto:" ni Markdown.
        """.trimIndent()

    /**
     * Prompt para extraer un único hecho personal declarativo del último mensaje del usuario.
     * Devuelve `{"fact": null}` si no hay nada claro — el caller lo interpreta como "no guardar".
     */
    fun buildFactExtraction(userText: String): String =
        """
        Extraé un único hecho personal del mensaje del usuario si está claro y declarativo.
        Categorías permitidas: hobby, familia, ciudad, cumpleaños, mascota, otro.
        Si NO hay hecho claro (saludo, pregunta, charla casual), devolvé {"fact": null}.

        Ejemplos:
        Usuario: "Me gusta mucho el tango" -> {"fact":{"category":"hobby","fact":"tango"}}
        Usuario: "Tengo una nieta que se llama Sofía" -> {"fact":{"category":"familia","fact":"nieta Sofía"}}
        Usuario: "Hola Beto, ¿qué tal?" -> {"fact": null}
        Usuario: "Vivo en Mar del Plata" -> {"fact":{"category":"ciudad","fact":"Mar del Plata"}}
        Usuario: "Me llamó mi hijo recién" -> {"fact": null}

        Respondé solo JSON válido.

        Usuario: $userText
        """.trimIndent()
}
