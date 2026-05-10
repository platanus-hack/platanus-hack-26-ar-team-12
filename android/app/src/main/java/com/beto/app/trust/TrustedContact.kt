package com.beto.app.trust

import kotlinx.serialization.Serializable

/**
 * Contacto de confianza al que Beto llama desde el botón "Llamar a alguien de confianza"
 * del Escudo Antiestafas. Es DELIBERADAMENTE solo uno (v1) — el pitch promete una acción
 * obvia, no un menú. Si en v2 queremos lista, esto se vuelve `List<TrustedContact>`.
 *
 * - `displayName`: como aparece en la libreta del user (lo que el system picker devolvió).
 * - `phoneNumberRaw`: el número como vino del picker (puede traer espacios, guiones, paréntesis).
 *   Lo normalizamos a la hora de invocar `tel:` — no en el modelo, para no perder el formato
 *   original que el user reconoce visualmente.
 * - `relationship`: etiqueta corta y cálida para el overlay y la voz: "Mi nieto", "Mi hija", etc.
 *   Va con voseo / vocabulario simple (CLAUDE.md).
 */
@Serializable
data class TrustedContact(
    val displayName: String,
    val phoneNumberRaw: String,
    val relationship: Relationship,
) {

    val callLabel: String
        get() = "Llamar a ${relationship.label.lowercase().replaceFirstChar { it.titlecase() }}"

    val voiceLabel: String
        get() = "${relationship.label.lowercase()} ${displayName.firstName()}"

    private fun String.firstName(): String = trim().split(" ").firstOrNull().orEmpty()

    @Serializable
    enum class Relationship(val label: String) {
        NIETO("Mi nieto"),
        NIETA("Mi nieta"),
        HIJO("Mi hijo"),
        HIJA("Mi hija"),
        HERMANO("Mi hermano"),
        HERMANA("Mi hermana"),
        SOBRINO("Mi sobrino"),
        SOBRINA("Mi sobrina"),
        VECINO("Mi vecino de confianza"),
        VECINA("Mi vecina de confianza"),
        AMIGO("Un amigo"),
        AMIGA("Una amiga"),
        OTRO("Alguien de confianza"),
    }
}
