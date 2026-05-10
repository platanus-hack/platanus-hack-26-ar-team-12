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

        val decision = parseDecision(response).also {
            if (it == null) Timber.tag(LogTags.LLM).w("LLM_PARSE_FAILED raw=%s", response.take(300))
        }
            ?: parseDecision(retryPrompt(prompt, response))
            ?: Decision.Unknown

        // Solo cachear decisiones útiles — un Unknown cacheado envenena retries del mismo input.
        if (decision !is Decision.Unknown) {
            cache.put(sanitized, decision)
        }
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
        private const val INTERPRET_SYSTEM = """
Sos un parser estricto. Respondé EXCLUSIVAMENTE con un objeto JSON válido que matchee el schema indicado en el user prompt. Sin markdown, sin ```json, sin texto antes o después, sin explicaciones. Solo el objeto JSON crudo empezando con { y terminando con }.
"""

        private fun defaultGenerator(): suspend (String) -> String = { prompt ->
            AnthropicClientHolder.complete(
                prompt = prompt,
                system = INTERPRET_SYSTEM.trim(),
                maxTokens = 512,
            )
        }
    }
}
