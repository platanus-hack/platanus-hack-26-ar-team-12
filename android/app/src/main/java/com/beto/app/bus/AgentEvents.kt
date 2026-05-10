package com.beto.app.bus

/**
 * Eventos que cualquier componente puede emitir al AgentBus.
 *
 * Phase 1 mergea las 8 variantes acá. Phase 2 agrega voz final para Plan C.
 * Phase 3-4 agregan más como:
 *   - IntentClassified(toolCall), ActionExecuted, ActionFailed
 *   - TreeSnapshot, AgenticIterationComplete, AgenticAborted
 */
sealed class AgentEvent {
    /** Emitido cuando BetoForegroundService completó startup y TTS está pronto. */
    object BootCompleted : AgentEvent()

    /** Emitido por MainActivity / PreflightCheck cuando faltan permisos críticos. */
    data class PermissionsMissing(val missing: List<String>) : AgentEvent()

    /** Tap corto en la burbuja flotante. Phase 2 lo conecta a captura de voz. */
    data class BubbleTapped(val startedAtMs: Long? = null) : AgentEvent()

    /** Long-press en la burbuja flotante. Phase 3 lo conecta al Modo Compañero. */
    object BubbleLongPressed : AgentEvent()

    /** La burbuja fue soltada en la zona inferior central para cerrar Beto. */
    object BubbleCloseRequested : AgentEvent()

    /** TTS empezó a pronunciar (onStart). Phase 4 lo usa para transicionar bubble state a SPEAKING. */
    data class TtsStarted(val utteranceId: String) : AgentEvent()

    /** TTS pronunció una frase exitosamente. */
    data class TtsSpoke(val text: String) : AgentEvent()

    /** TTS falló. Razón típica: init no completado, voz no descargada, idioma no soportado. */
    data class TtsFailed(val reason: String) : AgentEvent()

    /** SpeechRecognizer se conectó y empezó a escuchar (Phase 4 lo usa para LISTENING state). */
    object VoiceCaptureStarted : AgentEvent()

    /** Resultado final de Android SpeechRecognizer/RecognizerIntent. */
    data class VoiceCaptured(val text: String, val elapsedMs: Long) : AgentEvent()

    /** Se disparó corrección de STT para un transcript ambiguo. */
    data class SttCorrectionStarted(val raw: String, val confidence: Float?) : AgentEvent()

    /** Captura de voz cancelada, vacía, o fallida. */
    data class VoiceCaptureFailed(val reason: String, val elapsedMs: Long) : AgentEvent()

    /** Captura de voz superó el timeout sin detectar speech (Phase 4: vuelve a IDLE). */
    object VoiceCaptureTimeout : AgentEvent()

    /** Un Intent fue disparado exitosamente (WhatsApp/SMS/llamada/Maps). */
    data class IntentLaunched(val tool: String) : AgentEvent()

    /** Un tool del LLM o un Intent falló al ejecutarse. */
    data class ToolFailed(val tool: String, val reason: String) : AgentEvent()

    /** BetoAccessibilityService o BetoForegroundService onCreate / onServiceConnected. */
    object ServiceStarted : AgentEvent()

    /** BetoAccessibilityService o BetoForegroundService onDestroy. */
    object ServiceStopped : AgentEvent()

    // TODO Phase 2-3: IntentClassified(toolCall: ToolCall), ActionExecuted(name: String), ToolFailed(name: String, reason: String)
    // TODO Phase 4: TreeSnapshot(nodeRefs: List<NodeRef>), AgenticIterationComplete(iter: Int), AgenticAborted(reason: String)
}

/**
 * Comandos que un componente envía a otro vía bus. Diferencia con AgentEvent:
 * los Commands son intent-to-do, los Events son already-happened.
 */
sealed class AgentCommand {
    data class Speak(val text: String) : AgentCommand()
    data class StartVoiceCapture(val startedAtMs: Long? = null) : AgentCommand()

    /** Long-press en la burbuja → abre el Modo Compañero (Phase 4-03). */
    object OpenCompanion : AgentCommand()
}
