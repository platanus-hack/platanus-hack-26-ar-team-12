package com.beto.app.llm

/**
 * LLM boundary for action interpretation.
 *
 * Hackathon note: Firebase AI config can expose Gemini access from the APK. This is accepted
 * for the controlled demo; production should add App Check / backend mediation.
 */
interface LlmClient {
    suspend fun interpret(rawTranscript: String): Decision
}
