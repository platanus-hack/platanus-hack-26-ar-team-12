package com.beto.app.scam

import com.beto.app.llm.LlmClient
import com.beto.app.llm.Sanitizer
import com.beto.app.util.LogTags
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Capa OPCIONAL de redacción warm sobre la decisión que ya tomó el motor local.
 *
 * Regla de oro del pitch (slide 6): el LLM **NUNCA decide si hay riesgo** — eso es del
 * `ScamRiskEngine`. El LLM solo recibe la `RiskAssessment` ya emitida y arma una frase
 * cálida y específica al contexto para el overlay y el TTS.
 *
 * Si el LLM falla, supera el timeout, o devuelve algo inválido, el caller cae al fallback
 * canned (`ScamAlertOverlay.defaultBodyFor`). El escudo **funciona offline al 100%** —
 * el explainer es enrichment, no requirement.
 */
class ScamExplainer(
    private val llm: LlmClient,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {

    /**
     * Pide al LLM una frase warm contextual. Devuelve null si:
     *  - timeout / red caída
     *  - JSON inválido
     *  - phrase vacía o demasiado larga (sospecha de hallucination)
     */
    suspend fun explain(
        assessment: RiskAssessment,
        rawText: String,
        trustedContactDescription: String? = null,
    ): String? {
        if (assessment.signals.isEmpty()) return null

        val sanitized = Sanitizer.sanitize(rawText.take(MAX_TEXT_CHARS))
        val prompt = buildPrompt(assessment, sanitized, trustedContactDescription)

        val raw = withTimeoutOrNull(timeoutMs) {
            runCatching { llm.generatePhrase(prompt) }.getOrNull()
        }

        if (raw.isNullOrBlank()) {
            Timber.tag(LogTags.LLM).w("SCAM_EXPLAIN timeout or empty response")
            return null
        }

        val parsed = parsePhrase(raw)
        if (parsed == null) {
            Timber.tag(LogTags.LLM).w("SCAM_EXPLAIN parse failed raw=%s", raw.take(120))
            return null
        }
        if (parsed.length !in MIN_PHRASE_LEN..MAX_PHRASE_LEN) {
            Timber.tag(LogTags.LLM).w("SCAM_EXPLAIN phrase length out of bounds len=%d", parsed.length)
            return null
        }
        Timber.tag(LogTags.LLM).i("SCAM_EXPLAIN ok len=%d", parsed.length)
        return parsed
    }

    /**
     * Visible para test. Construye el prompt enviado a Sonnet — el JSON schema es estricto
     * para que el parser pueda extraer la phrase con confianza.
     */
    fun buildPrompt(
        assessment: RiskAssessment,
        sanitizedText: String,
        trustedContactDescription: String?,
    ): String = buildString {
        appendLine(SYSTEM_PROMPT)
        appendLine()
        appendLine("Señales detectadas (motor local, ya decidió que es estafa):")
        assessment.signals.distinct().forEach { signal ->
            appendLine("- ${signal.chipLabel} (${signal.name})")
        }
        appendLine()
        appendLine("Mensaje sospechoso (datos sensibles reemplazados por [TEL]/[DNI]/[TARJETA]):")
        appendLine("\"${sanitizedText.take(400)}\"")
        appendLine()
        if (!trustedContactDescription.isNullOrBlank()) {
            appendLine("Contacto de confianza disponible: $trustedContactDescription")
            appendLine("Podés mencionarlo cálidamente, ej: \"llamemos a $trustedContactDescription\".")
        } else {
            appendLine("No hay contacto de confianza configurado todavía.")
        }
        appendLine()
        appendLine("Devolvé solo este JSON: {\"phrase\":\"...\"}")
    }

    private fun parsePhrase(raw: String): String? = runCatching {
        val cleaned = stripFences(raw)
        JSON.decodeFromString(PhraseEnvelope.serializer(), cleaned).phrase.trim().ifBlank { null }
    }.getOrNull()

    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return trimmed
        return trimmed.substring(start, end + 1)
    }

    @Serializable
    private data class PhraseEnvelope(val phrase: String)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 1_500L
        private const val MAX_TEXT_CHARS = 600
        private const val MIN_PHRASE_LEN = 15
        private const val MAX_PHRASE_LEN = 220

        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        private const val SYSTEM_PROMPT = """
Sos Beto, un asistente cálido para adultos mayores en Argentina (voseo, vocabulario simple).
El motor local YA decidió que el mensaje en cuestión es probablemente una estafa.
Tu único trabajo es escribir UNA frase corta (1 a 2 oraciones, máximo 35 palabras) que:
  - explique al adulto mayor por qué frenamos (basado en las señales detectadas)
  - lo invite a llamar a alguien de confianza (si hay contacto, mencionalo cálidamente)
  - NO use lenguaje alarmista, mayúsculas ni signos de exclamación múltiples
  - NO repita literalmente el mensaje sospechoso
  - NO use vocabulario técnico ("phishing", "ingeniería social", "URL")
  - Use voseo argentino: "frenemos", "llamemos", "mejor no contestes"
NUNCA cambies tu decisión: el motor ya dijo que es estafa. Tu rol es solo redactar.
Devolvé EXCLUSIVAMENTE JSON válido con schema {"phrase":"..."}, sin Markdown ni comentarios.
"""
    }
}
