package com.beto.app.llm

import com.beto.app.util.LogTags
import com.beto.app.voice.TranscriptCorrectionClient
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import timber.log.Timber

class GeminiLlmClient(
    private val cache: LlmCache = LlmCache(),
    private val generateContent: suspend (String) -> String = defaultGenerator(),
) : LlmClient, TranscriptCorrectionClient {

    override suspend fun interpret(rawTranscript: String): Decision {
        val sanitized = Sanitizer.sanitize(rawTranscript)
        cache.get(sanitized)?.let {
            Timber.tag(LogTags.LLM).d("LLM_CACHE_HIT")
            return it
        }

        val prompt = PromptBuilder.buildInterpretPrompt(sanitized)
        val response = runCatching { generateContent(prompt) }
            .getOrElse { error ->
                Timber.tag(LogTags.LLM).w(error, "LLM_GENERATE_FAILED")
                return Decision.Unknown
            }

        val decision = parseDecision(response)
            ?: parseDecision(retryPrompt(prompt, response))
            ?: Decision.Unknown

        cache.put(sanitized, decision)
        return decision
    }

    override suspend fun correctTranscript(prompt: String): String =
        runCatching { generateContent(prompt) }
            .getOrElse {
                Timber.tag(LogTags.LLM).w(it, "LLM_STT_CORRECTION_FAILED")
                """{"corrected":""}"""
            }

    private fun parseDecision(raw: String): Decision? = DecisionJson.decode(raw)

    private suspend fun retryPrompt(prompt: String, malformedResponse: String): String =
        runCatching {
            generateContent(
                """
                $prompt

                La respuesta anterior fue JSON inválido o contenía una tool no permitida:
                $malformedResponse

                Respondé de nuevo solo con JSON válido y una tool permitida.
                """.trimIndent(),
            )
        }.getOrDefault("")

    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash"

        private fun defaultGenerator(): suspend (String) -> String {
            val model: GenerativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = MODEL_NAME,
                    generationConfig = generationConfig {
                        temperature = 0f
                        maxOutputTokens = 512
                        responseMimeType = "application/json"
                    },
                )
            return { prompt -> model.generateContent(prompt).text.orEmpty() }
        }
    }
}

/*
Fallback sketch if Gemini underperforms in es-AR:

class AnthropicLlmClient : LlmClient {
    override suspend fun interpret(rawTranscript: String): Decision = Decision.Unknown
}
*/
