package com.beto.app.companion

import com.beto.app.llm.PromptBuilder
import com.beto.app.llm.Sanitizer
import com.beto.app.util.LogTags
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

interface CompanionChatClient {
    suspend fun chat(history: List<CompanionMessage>): String
    suspend fun extractProfileFact(userText: String): ProfileFact?
}

/**
 * Cliente del Modo Compañero. Modelo separado del Motor de Acciones para no interferir:
 *  - `gemini-2.5-flash-lite` con `temperature: 0.4` para chat (tono natural).
 *  - `gemini-2.5-flash-lite` con `temperature: 0` para extracción JSON de hechos.
 *
 * Sanitizer aplicado a CADA mensaje del historial antes de mandarlo al LLM.
 */
class CompanionLlmClient(
    private val sanitizer: Sanitizer = Sanitizer,
    private val chatGenerator: suspend (String) -> String = defaultChatGenerator(),
    private val factGenerator: suspend (String) -> String = defaultFactGenerator(),
) : CompanionChatClient {

    override suspend fun chat(history: List<CompanionMessage>): String {
        val sanitized = history.map { it.copy(text = sanitizer.sanitize(it.text)) }
        val prompt = PromptBuilder.buildCompanionChat(sanitized)
        return runCatching { chatGenerator(prompt) }
            .getOrElse {
                Timber.tag(LogTags.LLM).w(it, "COMPANION_CHAT_FAILED")
                ""
            }
            .ifBlank { "Estoy acá. Contame un poco más." }
    }

    override suspend fun extractProfileFact(userText: String): ProfileFact? {
        val sanitized = sanitizer.sanitize(userText)
        val prompt = PromptBuilder.buildFactExtraction(sanitized)
        val raw = runCatching { factGenerator(prompt) }
            .getOrElse {
                Timber.tag(LogTags.LLM).w(it, "COMPANION_FACT_FAILED")
                ""
            }
        return parseFact(raw)
    }

    private fun parseFact(raw: String): ProfileFact? = runCatching {
        val obj = Json.parseToJsonElement(raw.trim()).jsonObject
        val factObj = obj["fact"]?.jsonObject ?: return@runCatching null
        val category = factObj["category"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val fact = factObj["fact"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        ProfileFact(category = category, fact = fact)
    }.getOrNull()

    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash-lite"

        private fun defaultChatGenerator(): suspend (String) -> String {
            val model: GenerativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = MODEL_NAME,
                    generationConfig = generationConfig {
                        temperature = 0.4f
                        maxOutputTokens = 200
                    },
                )
            return { prompt -> model.generateContent(prompt).text.orEmpty() }
        }

        private fun defaultFactGenerator(): suspend (String) -> String {
            val model: GenerativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = MODEL_NAME,
                    generationConfig = generationConfig {
                        temperature = 0f
                        maxOutputTokens = 80
                        responseMimeType = "application/json"
                    },
                )
            return { prompt -> model.generateContent(prompt).text.orEmpty() }
        }
    }
}
