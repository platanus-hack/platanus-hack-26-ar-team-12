package com.beto.app.voice

import com.beto.app.llm.LlmClient
import com.beto.app.llm.PromptBuilder
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

/**
 * Genera frases TTS contextuales para Beto. Usa Gemini con cache LRU + fallbacks
 * hardcoded en `PhraseFallbacks` cuando la red falla o el LLM tarda > timeout.
 *
 * Quality checks rechazan outputs que violen el tono argentino (presencia de "usted",
 * frases muy largas, marcadores de redacción residuales).
 */
class PhraseGenerator(
    private val llm: LlmClient,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxCacheEntries: Int = DEFAULT_MAX_CACHE,
) {
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, String>(maxCacheEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxCacheEntries
    }

    suspend fun forIntent(intent: PhraseIntent, params: PhraseParams = PhraseParams.empty()): String {
        val key = key(intent, params)

        mutex.withLock { cache[key] }?.let {
            Timber.tag(LogTags.TTS).d("PHRASE_CACHE_HIT intent=%s", intent)
            return it
        }

        val generated = generateOrFallback(intent, params)
        mutex.withLock { cache[key] = generated }
        return generated
    }

    /** Pre-genera frases comunes en background (no bloquea boot). */
    fun warmCache(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch {
            COMMON_COLD_START.forEach { (intent, params) ->
                runCatching { forIntent(intent, params) }
            }
        }
    }

    private suspend fun generateOrFallback(intent: PhraseIntent, params: PhraseParams): String {
        val fallback = PhraseFallbacks.forIntent(intent, params)
        return runCatching {
            withTimeout(timeoutMs) {
                val raw = llm.generatePhrase(PromptBuilder.buildPhraseGenPrompt(intent, params))
                val parsed = parsePhrase(raw)
                if (parsed != null && passesQualityChecks(parsed)) {
                    Timber.tag(LogTags.TTS).d("PHRASE_GENERATED intent=%s", intent)
                    parsed
                } else {
                    Timber.tag(LogTags.TTS).d("PHRASE_GEN_REJECTED intent=%s", intent)
                    fallback
                }
            }
        }.getOrElse {
            Timber.tag(LogTags.TTS).d("PHRASE_GEN_FALLBACK intent=%s reason=%s", intent, it::class.simpleName)
            fallback
        }
    }

    private fun parsePhrase(raw: String): String? =
        runCatching {
            Json.parseToJsonElement(raw.trim())
                .jsonObject["phrase"]
                ?.jsonPrimitive
                ?.content
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun key(intent: PhraseIntent, params: PhraseParams): String =
        "${intent.name}|${params.canonical()}"

    companion object {
        const val DEFAULT_TIMEOUT_MS = 800L
        const val DEFAULT_MAX_CACHE = 100
        private const val MAX_WORDS = 20
        private const val MIN_WORDS = 2

        // Pre-warm: frases que se usan en TODO flujo, sin parámetros específicos.
        private val COMMON_COLD_START: List<Pair<PhraseIntent, PhraseParams>> = listOf(
            PhraseIntent.UNKNOWN_COMMAND to PhraseParams.empty(),
            PhraseIntent.FAILED_INTENT to PhraseParams.empty(),
            PhraseIntent.SUCCESS_WHATSAPP to PhraseParams.empty(),
            PhraseIntent.SUCCESS_CALL to PhraseParams.empty(),
        )

        // "usted" como palabra completa (case-insensitive)
        private val FORMAL_USTED = Regex("""\busted(es)?\b""", RegexOption.IGNORE_CASE)
        // "su X" en sentido formal (típicos sustantivos dirigidos al interlocutor)
        private val FORMAL_SU = Regex(
            """\b(su|sus)\s+(mensaje|llamada|tel[eé]fono|contacto|familia|nieto|hijo|hija|cuenta)\b""",
            RegexOption.IGNORE_CASE,
        )

        fun passesQualityChecks(phrase: String): Boolean {
            val words = phrase.split(Regex("\\s+")).count { it.isNotBlank() }
            if (words < MIN_WORDS || words > MAX_WORDS) return false
            if (FORMAL_USTED.containsMatchIn(phrase)) return false
            if (FORMAL_SU.containsMatchIn(phrase)) return false
            // Marcadores residuales que no deberían llegar al user
            if ("[DNI]" in phrase || "[TEL]" in phrase || "[TARJETA]" in phrase) return false
            return true
        }
    }
}
