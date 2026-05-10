package com.beto.app.companion

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * Mensaje individual del Modo Compañero.
 *
 * `pendingFactConfirmation`: si se setea, la UI muestra una tarjeta inline preguntando
 * "¿Querés que me acuerde de esto?". Solo si el user confirma, se guarda en `UserMemoryStore`.
 * Esto cumple COMP-04 — el usuario decide explícitamente qué se persiste.
 */
@Immutable
data class CompanionMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val pendingFactConfirmation: ProfileFact? = null,
)

enum class Role { USER, BETO }

/** Hecho personal extraído de un mensaje del usuario que Beto puede recordar opt-in. */
@Immutable
data class ProfileFact(
    val category: String,
    val fact: String,
)
