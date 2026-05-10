package com.beto.app.llm

import com.beto.app.util.LogTags
import com.beto.app.voice.TranscriptCorrectionClient
import timber.log.Timber

class ClaudeLlmClient(
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

    override suspend fun generatePhrase(prompt: String): String =
        runCatching { generateContent(prompt) }
            .getOrElse {
                Timber.tag(LogTags.LLM).w(it, "LLM_PHRASE_GEN_FAILED")
                ""
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
        private fun defaultGenerator(): suspend (String) -> String = { prompt ->
            AnthropicClientHolder.complete(
                prompt = prompt,
                maxTokens = 512,
            )
        }
    }
}
