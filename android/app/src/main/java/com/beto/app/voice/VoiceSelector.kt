package com.beto.app.voice

import android.speech.tts.Voice

/**
 * Elige la mejor voz TTS disponible para Beto.
 *
 * Prioridad (de mayor a menor):
 *  1. Voz **masculina + neural + es-AR**.
 *  2. Voz **masculina + neural + es-419 / es-MX / es-CL**.
 *  3. Voz **masculina + neural** en cualquier `es`.
 *  4. Voz **masculina** (no neural) en cualquier `es`.
 *  5. Cualquier voz neural en `es` (último recurso si no hay masculinas).
 *  6. Cualquier voz `es`.
 *
 * Detección de género: heurística por nombre + lista hardcoded de IDs masculinos
 * conocidos (Google neural sigue convenciones tipo `es-AR-Tomas-Neural`).
 *
 * Razón del filtro masculino: Beto tiene persona explícita de "amigo argentino"
 * (PROJECT.md / VOICE-HUM-01) — voz femenina rompe la inmersión del personaje.
 */
object VoiceSelector {

    /** IDs conocidos de voces masculinas Google Neural / Samsung Neural. Lista viva. */
    val KNOWN_MALE_IDS: Set<String> = setOf(
        "es-AR-Tomas-Neural", "es-AR-Tomas",
        "es-MX-Jorge-Neural", "es-MX-Jorge",
        "es-US-Neural2-B", "es-US-Neural2-C",
        "es-ES-Alvaro-Neural", "es-ES-Alvaro",
        "es-CL-Lorenzo-Neural",
        "es-CO-Gonzalo-Neural",
        // Google TTS local (offline) — naming scheme es-XX-language-X-male/female
        "es-es-x-eea-local",
        "es-es-x-eee-local",
        "es-us-x-esd-local",
        "es-us-x-esf-local",
    )

    /** Tokens en el nombre de la voz que hacen probable que sea masculina. */
    val MALE_NAME_HINTS: Set<String> = setOf(
        "tomas", "jorge", "alvaro", "andres", "diego", "pablo",
        "carlos", "javier", "manuel", "miguel", "lorenzo", "gonzalo",
        "luis", "juan", "daniel", "fernando", "raul", "sergio",
        "male", "_m_", "-m-", " m ", "man",
        // Google offline naming includes -x-eea / -x-esd codes that are male
        "-eea-", "-eee-", "-esd-", "-esf-",
    )

    /** Tokens que hacen probable que sea femenina (deprioritized). */
    val FEMALE_NAME_HINTS: Set<String> = setOf(
        "elena", "maria", "sofia", "lucia", "camila", "valentina",
        "ana", "paula", "laura", "isabella", "luciana",
        "female", "_f_", "woman",
    )

    /** API de producción — recibe `Voice` real de Android. */
    fun selectBest(voices: Set<Voice>): Voice? =
        voices.toCandidates()
            .let { selectBestCandidate(it) }
            ?.let { picked -> voices.firstOrNull { it.name == picked.name && it.locale?.toLanguageTag() == picked.localeTag } }

    /** Helper de producción: probable masculina en base a heurística. */
    fun isLikelyMale(voice: Voice): Boolean = scoreGender(voice.name) >= 80

    /**
     * API testeable que opera sobre `VoiceCandidate` (sin dependencia de Android Voice).
     *
     * Orden de prioridad: género (masculino > desconocido > femenino) → neural → locale → quality →
     * offline. Género va primero porque la persona del producto exige voz masculina; preferimos
     * un masculino es-MX antes que un femenino es-AR.
     */
    internal fun selectBestCandidate(candidates: List<VoiceCandidate>): VoiceCandidate? =
        candidates
            .filter { it.localeTag.startsWithSpanish() }
            .sortedWith(
                compareByDescending<VoiceCandidate> { scoreGender(it.name) }
                    .thenByDescending { isNeural(it.name, it.features) }
                    .thenByDescending { scoreLocale(it.localeTag) }
                    .thenByDescending { it.quality }
                    .thenBy { it.networkRequired },
            )
            .firstOrNull()

    internal fun scoreLocale(tag: String?): Int = when (tag) {
        "es-AR" -> 100
        "es-419" -> 80
        "es-MX" -> 70
        "es-CL", "es-CO", "es-PE", "es-UY" -> 60
        "es-US" -> 50
        "es-ES" -> 40
        null -> 0
        else -> if (tag.startsWith("es")) 20 else 0
    }

    /** 100 = match exacto, 80 = nombre masculino, 50 = unknown, 0 = femenino claro. */
    internal fun scoreGender(name: String): Int {
        if (name in KNOWN_MALE_IDS) return 100
        val lc = name.lowercase()
        if (FEMALE_NAME_HINTS.any { it in lc }) return 0
        if (MALE_NAME_HINTS.any { it in lc }) return 80
        return 50
    }

    internal fun isNeural(name: String, features: Set<String>): Boolean =
        name.contains("neural", ignoreCase = true) ||
            name.contains("wavenet", ignoreCase = true) ||
            features.any { it.contains("network-tts", ignoreCase = true) }

    private fun String?.startsWithSpanish(): Boolean = this?.startsWith("es") == true

    private fun Set<Voice>.toCandidates(): List<VoiceCandidate> = map {
        VoiceCandidate(
            name = it.name,
            localeTag = it.locale?.toLanguageTag(),
            quality = it.quality,
            networkRequired = it.isNetworkConnectionRequired,
            features = it.features.orEmpty(),
        )
    }
}

/** Snapshot inmutable de una `Voice` para scoring testeable sin Android. */
internal data class VoiceCandidate(
    val name: String,
    val localeTag: String?,
    val quality: Int,
    val networkRequired: Boolean,
    val features: Set<String>,
)
