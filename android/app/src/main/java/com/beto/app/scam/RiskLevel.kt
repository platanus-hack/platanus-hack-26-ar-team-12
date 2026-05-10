package com.beto.app.scam

import kotlinx.serialization.Serializable

/**
 * Nivel de riesgo final que retorna el ScamRiskEngine.
 * Threshold del pitch (slide 7): "una señal sola es ruido · tres señales son patrón".
 */
@Serializable
enum class RiskLevel {
    /** Cero señales detectadas. */
    NONE,

    /** Una señal: puede ser ruido. En v1 no disparamos overlay proactivo. */
    LOW,

    /** Dos señales: sospechoso. Reservado para el path reactivo (user pregunta). */
    MEDIUM,

    /** Tres o más: patrón claro de estafa. Dispara el overlay proactivo. */
    HIGH,
}
