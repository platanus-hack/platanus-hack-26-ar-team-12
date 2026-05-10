package com.beto.app.scam

import kotlinx.serialization.Serializable

/**
 * Catálogo de señales de estafa que el motor local sabe reconocer en es-AR.
 * `chipLabel` es la etiqueta que el overlay muestra al adulto mayor.
 */
@Serializable
enum class Signal(val chipLabel: String) {
    URGENCY("urgencia"),
    MONEY_REQUEST("pedido de dinero"),
    CODE_REQUEST("pedido de código"),
    NEW_NUMBER("\"cambié de número\""),
    SECRECY("\"no le digas a nadie\""),
    REMOTE_CONTROL("AnyDesk / control remoto"),
    SUSPICIOUS_LINK("link sospechoso"),
    IMPERSONATION_FAMILY("se hace pasar por familiar"),
    AUTHORITY_IMPERSONATION("se hace pasar por organismo"),
    PRIZE_BAIT("premio o sorteo"),
    THREAT("amenaza o embargo"),
}
