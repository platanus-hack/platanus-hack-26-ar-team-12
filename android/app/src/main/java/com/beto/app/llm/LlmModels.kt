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
            json.decodeFromString(Decision.serializer(), raw.trim())
        }.getOrNull()?.takeIf(::isAllowed)

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
            else -> emptySet()
        }
}
