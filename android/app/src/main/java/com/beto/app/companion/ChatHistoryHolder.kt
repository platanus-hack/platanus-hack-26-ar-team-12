package com.beto.app.companion

/**
 * Singleton mutable que guarda un snapshot del historial del Modo Compañero.
 * Evita acoplar `ActionDispatcher` (que vive en el FGS) con el `CompanionViewModel`
 * (que vive en la Activity) — la VM publica acá cuando el historial cambia, y el
 * dispatcher lo lee solo cuando entra en chatMode y el LLM devuelve Unknown.
 *
 * Pensamiento de diseño: snapshot inmutable + lock corto. NO es la fuente de verdad
 * (esa sigue siendo la VM); es solo un mirror para que el dispatcher tenga contexto.
 */
object ChatHistoryHolder {

    @Volatile
    private var snapshot: List<CompanionMessage> = emptyList()

    fun update(messages: List<CompanionMessage>) {
        // Cap at 20 turns para limitar tokens en el prompt.
        snapshot = messages.takeLast(MAX_TURNS)
    }

    fun current(): List<CompanionMessage> = snapshot

    fun clear() {
        snapshot = emptyList()
    }

    private const val MAX_TURNS = 20
}
