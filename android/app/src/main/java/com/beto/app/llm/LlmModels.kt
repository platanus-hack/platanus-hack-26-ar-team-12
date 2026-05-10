package com.beto.app.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class Decision {
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val tool: String,
        val args: Map<String, String>,
    ) : Decision()

    @Serializable
    @SerialName("needs_clarification")
    data class NeedsClarification(
        val question: String,
        val expecting: ExpectedAnswer,
    ) : Decision()

    @Serializable
    @SerialName("unknown")
    data object Unknown : Decision()
}

@Serializable
enum class ExpectedAnswer {
    CONTACT_NAME,
    CHANNEL,
    FREE_TEXT,
}

object DecisionJson {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(decision: Decision): String = json.encodeToString(Decision.serializer(), decision)

    fun decode(raw: String): Decision? =
        runCatching {
            json.decodeFromString(Decision.serializer(), extractJsonObject(raw))
        }.getOrNull()?.takeIf(::isAllowed)

    /**
     * Tolerar respuestas LLM con preamble / markdown fences. Claude (a diferencia
     * de Gemini con responseMimeType=application/json) a veces devuelve:
     *   ```json\n{...}\n```
     *   "Aquí está: {...}"
     * Tomamos el substring desde el primer '{' balanceado hasta su cierre.
     */
    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        if (start < 0) return trimmed
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until trimmed.length) {
            val c = trimmed[i]
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return trimmed.substring(start, i + 1)
                }
            }
        }
        return trimmed.substring(start)
    }

    private fun isAllowed(decision: Decision): Boolean =
        when (decision) {
            is Decision.ToolCall -> {
                decision.tool in ToolDescriptors.ALLOWED_TOOLS &&
                    requiredArgsFor(decision.tool).all { it in decision.args.keys }
            }
            is Decision.NeedsClarification -> decision.question.isNotBlank()
            Decision.Unknown -> true
        }

    private fun requiredArgsFor(tool: String): Set<String> =
        when (tool) {
            ToolDescriptors.SEND_WHATSAPP -> setOf("contact", "message")
            ToolDescriptors.MAKE_CALL -> setOf("contact")
            ToolDescriptors.SEND_SMS -> setOf("contact", "message")
            ToolDescriptors.OPEN_MAPS -> setOf("query")
            ToolDescriptors.SHOW_HOW_TO -> setOf("action")
            else -> emptySet()
        }
}
