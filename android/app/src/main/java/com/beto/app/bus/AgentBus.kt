package com.beto.app.bus

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

/**
 * In-process pub/sub singleton. ÚNICO punto de comunicación entre BetoForegroundService,
 * BetoAccessibilityService, MainActivity, VoiceCaptureActivity (Phase 2), CompanionActivity (Phase 3).
 *
 * NO usar Binder, AIDL, broadcasts custom — todo va por acá.
 *
 * Replay = 0 (no buffer histórico) + extraBufferCapacity para evitar bloquear emisores rápidos.
 * BufferOverflow.DROP_OLDEST garantiza que si un consumer es lento, no bloquea al emisor.
 */
object AgentBus {
    private val _events = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    private val _commands = MutableSharedFlow<AgentCommand>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val commands: SharedFlow<AgentCommand> = _commands.asSharedFlow()

    suspend fun emit(event: AgentEvent) {
        Timber.tag("Beto-Bus").d("emit -> %s", event::class.simpleName)
        _events.emit(event)
    }

    suspend fun command(cmd: AgentCommand) {
        Timber.tag("Beto-Bus").d("command -> %s", cmd::class.simpleName)
        _commands.emit(cmd)
    }
}
