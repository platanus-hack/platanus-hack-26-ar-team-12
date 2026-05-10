package com.beto.app.scam

import kotlinx.serialization.Serializable

/** Match concreto de una Signal en el texto. `evidence` es el snippet que disparó. */
@Serializable
data class SignalHit(
    val signal: Signal,
    val evidence: String,
)

/**
 * Resultado del análisis de un texto. Lo consume el AlertOrchestrator (Block 6) para
 * decidir si mostrar overlay, y el LLM Explainer (Block 5) para redactar la frase warm.
 */
@Serializable
data class RiskAssessment(
    val level: RiskLevel,
    val hits: List<SignalHit>,
    val analyzedLength: Int,
) {
    val signals: List<Signal> get() = hits.map { it.signal }

    /** Regla del pitch: solo disparamos overlay sin pedirle al user cuando level == HIGH. */
    val shouldAlertProactively: Boolean get() = level == RiskLevel.HIGH

    companion object {
        fun empty(analyzedLength: Int = 0): RiskAssessment =
            RiskAssessment(level = RiskLevel.NONE, hits = emptyList(), analyzedLength = analyzedLength)
    }
}
