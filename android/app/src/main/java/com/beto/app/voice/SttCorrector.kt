package com.beto.app.voice

import com.beto.app.action.DeterministicMatcher
import com.beto.app.util.LogTags
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.text.Normalizer
import java.util.Locale

data class SttContext(
    val knownContacts: List<String>,
    val lastCommand: String?,
)

interface TranscriptCorrectionClient {
    suspend fun correctTranscript(prompt: String): String
}

class SttCorrector(
    private val client: TranscriptCorrectionClient,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun correct(raw: String, context: SttContext): String {
        val prompt = SttCorrectionPromptBuilder.build(raw, context)
        val corrected = runCatching {
            withTimeout(timeoutMs) {
                parseCorrected(client.correctTranscript(prompt))
            }
        }.getOrElse { error ->
            Timber.tag(LogTags.STT).w(error, "STT correction fallback reason=%s", error::class.simpleName)
            return raw
        } ?: return raw

        return if (isSafeCorrection(raw, corrected, context)) corrected else raw
    }

    private fun parseCorrected(response: String): String? =
        runCatching {
            Json.parseToJsonElement(response)
                .jsonObject["corrected"]
                ?.jsonPrimitive
                ?.content
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun isSafeCorrection(raw: String, corrected: String, context: SttContext): Boolean {
        val rawVerb = raw.normalizedTokens().firstOrNull()
        val correctedVerb = corrected.normalizedTokens().firstOrNull()
        if (rawVerb != null && correctedVerb != null && rawVerb != correctedVerb) return false

        val rawNormalized = DeterministicMatcher.normalize(raw)
        val correctedNormalized = DeterministicMatcher.normalize(corrected)
        val known = context.knownContacts.map { DeterministicMatcher.normalize(it) }.filter { it.isNotBlank() }
        val correctedContactCandidate = correctedNormalized.contactCandidate()
        val rawContactCandidate = rawNormalized.contactCandidate()
        if (
            correctedContactCandidate != null &&
            correctedContactCandidate != rawContactCandidate &&
            correctedContactCandidate !in known
        ) {
            return false
        }

        val introducedContacts = known.filter { contact ->
            correctedNormalized.containsWholeToken(contact) && !rawNormalized.containsWholeToken(contact)
        }
        if (introducedContacts.isEmpty()) return true

        val rawTokens = rawNormalized.split(' ').filter { it.isNotBlank() }
        return introducedContacts.all { contact ->
            rawTokens.any { token -> token.similarity(contact) >= CONTACT_SIMILARITY_THRESHOLD }
        }
    }

    private fun String.normalizedTokens(): List<String> =
        DeterministicMatcher.normalize(this).split(' ').filter { it.isNotBlank() }

    private fun String.containsWholeToken(token: String): Boolean =
        Regex("(^| )${Regex.escape(token)}( |$)").containsMatchIn(this)

    private fun String.contactCandidate(): String? {
        val tokens = split(' ').filter { it.isNotBlank() }
        val index = tokens.indexOf("a")
        return tokens.getOrNull(index + 1)
    }

    private fun String.similarity(other: String): Double {
        if (isBlank() || other.isBlank()) return 0.0
        val a = normalizeForDistance(this)
        val b = normalizeForDistance(other)
        val max = maxOf(a.length, b.length).coerceAtLeast(1)
        return 1.0 - (levenshtein(a, b).toDouble() / max.toDouble())
    }

    private fun normalizeForDistance(value: String): String =
        Normalizer.normalize(value.lowercase(Locale("es", "AR")), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")

    private fun levenshtein(a: String, b: String): Int {
        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = i - 1
            costs[0] = i
            for (j in 1..b.length) {
                val current = costs[j]
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    previous + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                previous = current
            }
        }
        return costs[b.length]
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 1_500L
        private const val CONTACT_SIMILARITY_THRESHOLD = 0.5
    }
}

object SttCorrectionPromptBuilder {
    fun build(raw: String, context: SttContext): String = buildString {
        appendLine("Sos un corrector de transcripts en español argentino.")
        appendLine("Corregí errores fonéticos obvios manteniendo la intención.")
        appendLine("Mantené verbos y estructura intactos.")
        appendLine("Si un nombre suena parecido a uno de los contactos conocidos, usá el contacto.")
        appendLine("Si no, dejá el nombre como está. NO inventes contactos.")
        appendLine("Respondé solo JSON con schema: {\"corrected\":\"string\"}.")
        appendLine()
        appendLine("Ejemplos:")
        appendLine("raw: llama a juana | contactos: Juan, Pedro | {\"corrected\":\"llama a juan\"}")
        appendLine("raw: mandale a mi nieto | contactos: Juan | {\"corrected\":\"mandale a mi nieto\"}")
        appendLine("raw: abrime wasap | contactos: | {\"corrected\":\"abrime whatsapp\"}")
        appendLine()
        appendLine("Contactos conocidos: ${context.knownContacts.joinToString(", ")}")
        appendLine("Ultimo comando: ${context.lastCommand.orEmpty()}")
        appendLine("raw: $raw")
    }
}

object NoOpTranscriptCorrectionClient : TranscriptCorrectionClient {
    override suspend fun correctTranscript(prompt: String): String =
        """{"corrected":""}"""
}
