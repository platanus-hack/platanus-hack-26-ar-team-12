package com.beto.app.scam

import java.text.Normalizer

/**
 * Cerebro local del Escudo Antiestafas. 100% on-device, offline.
 *
 * Decide si un texto tiene patrón de estafa cruzando señales independientes.
 * Threshold del pitch: 0=NONE, 1=LOW (ruido), 2=MEDIUM, 3+=HIGH (patrón).
 *
 * Diseño deliberadamente puro (no Android, no I/O): inyectable y unit-testeable.
 * El LLM nunca decide, solo enriquece la frase warm en una capa posterior.
 */
class ScamRiskEngine(
    private val detectors: List<SignalDetector> = SignalDetectors.DEFAULT,
) {

    fun assess(text: String): RiskAssessment {
        if (text.isBlank()) return RiskAssessment.empty()

        val normalized = normalize(text)
        val hits = detectors.mapNotNull { it.detect(normalized) }

        val level = when (hits.size) {
            0 -> RiskLevel.NONE
            1 -> RiskLevel.LOW
            2 -> RiskLevel.MEDIUM
            else -> RiskLevel.HIGH
        }

        return RiskAssessment(level = level, hits = hits, analyzedLength = text.length)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")

    private companion object {
        private val DIACRITICS = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    }
}
