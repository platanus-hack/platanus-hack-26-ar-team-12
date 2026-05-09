package com.beto.app.bus

/**
 * Eventos que cualquier componente puede emitir al AgentBus.
 *
 * Phase 1 mergea las 8 variantes acá. Phase 2-4 agregan más como TODOs:
 *   - VoiceCaptured(text), VoiceCaptureFailed
 *   - IntentClassified(toolCall), ActionExecuted, ActionFailed
 *   - TreeSnapshot, AgenticIterationComplete, AgenticAborted
 */
sealed class AgentEvent {
    /** Emitido cuando BetoForegroundService completó startup y TTS está pronto. */
    object BootCompleted : AgentEvent()

    /** Emitido por MainActivity / PreflightCheck cuando faltan permisos críticos. */
    data class PermissionsMissing(val missing: List<String>) : AgentEvent()

    /** Tap corto en la burbuja flotante. Phase 2 lo conecta a captura de voz. */
    object BubbleTapped : AgentEvent()

    /** Long-press en la burbuja flotante. Phase 3 lo conecta al Modo Compañero. */
    object BubbleLongPressed : AgentEvent()

    /** TTS pronunció una frase exitosamente. */
    data class TtsSpoke(val text: String) : AgentEvent()

    /** TTS falló. Razón típica: init no completado, voz no descargada, idioma no soportado. */
    data class TtsFailed(val reason: String) : AgentEvent()

    /** BetoAccessibilityService o BetoForegroundService onCreate / onServiceConnected. */
    object ServiceStarted : AgentEvent()

    /** BetoAccessibilityService o BetoForegroundService onDestroy. */
    object ServiceStopped : AgentEvent()

    // TODO Phase 2: VoiceCaptured(text: String), VoiceCaptureFailed(reason: String)
    // TODO Phase 2-3: IntentClassified(toolCall: ToolCall), ActionExecuted(name: String), ToolFailed(name: String, reason: String)
    // TODO Phase 4: TreeSnapshot(nodeRefs: List<NodeRef>), AgenticIterationComplete(iter: Int), AgenticAborted(reason: String)
}

/**
 * Comandos que un componente envía a otro vía bus. Diferencia con AgentEvent:
 * los Commands son intent-to-do, los Events son already-happened.
 */
sealed class AgentCommand {
    data class Speak(val text: String) : AgentCommand()
    object StartVoiceCapture : AgentCommand()

    // TODO Phase 2-4: ExecuteToolCall, RunAgenticLoop, OpenCompanion, etc.
}
