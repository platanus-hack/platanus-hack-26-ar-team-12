package com.beto.app.llm

/**
 * LLM boundary for action interpretation.
 *
 * Hackathon note: Firebase AI config can expose Gemini access from the APK. This is accepted
 * for the controlled demo; production should add App Check / backend mediation.
 */
interface LlmClient {
    suspend fun interpret(rawTranscript: String): Decision

    /**
     * Genera una frase TTS contextual a partir de un prompt completo armado por
     * `PromptBuilder.buildPhraseGenPrompt`. Devuelve un JSON crudo (string) que el
     * caller parsea — típicamente `{"phrase":"..."}`.
     *
     * Se permite devolver string vacío si el LLM falla — el caller cae a `PhraseFallbacks`.
     */
    suspend fun generatePhrase(prompt: String): String = ""
}
