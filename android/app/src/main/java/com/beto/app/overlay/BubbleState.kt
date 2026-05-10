package com.beto.app.overlay

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.beto.app.R

/**
 * Cinco estados visuales de la burbuja de Beto (OVERLAY-05 / D-14).
 *
 * Cada estado define:
 *  - `ringColorRes`: color del anillo exterior tintable (vía `View.backgroundTintList`).
 *  - `centerIconRes`: ícono pequeño en el badge bottom-right (refuerza el estado, no color-only).
 *    `null` para IDLE = sin badge visible.
 *  - `animation`: animación que corre mientras el estado está activo.
 *
 * El logo central NO cambia entre estados (D-13 — identidad estable de Beto).
 */
enum class BubbleState(
    @ColorRes val ringColorRes: Int,
    @DrawableRes val centerIconRes: Int?,
    val animation: BubbleAnimation,
) {
    IDLE(
        ringColorRes = R.color.beto_state_idle,
        centerIconRes = null,
        animation = BubbleAnimation.NONE,
    ),
    LISTENING(
        ringColorRes = R.color.beto_state_listening,
        centerIconRes = R.drawable.ic_state_listening,
        animation = BubbleAnimation.PULSE_SLOW,
    ),
    THINKING(
        ringColorRes = R.color.beto_state_thinking,
        centerIconRes = R.drawable.ic_state_thinking,
        animation = BubbleAnimation.PULSE_SCALE,
    ),
    SPEAKING(
        ringColorRes = R.color.beto_state_speaking,
        centerIconRes = R.drawable.ic_state_speaking,
        animation = BubbleAnimation.PULSE_FAST,
    ),
    ERROR(
        ringColorRes = R.color.beto_state_error,
        centerIconRes = R.drawable.ic_state_error,
        animation = BubbleAnimation.SHAKE,
    ),
}

enum class BubbleAnimation {
    /** Sin animación (idle). */
    NONE,
    /** Pulse alpha 0.65 ↔ 1.0 a 800ms — listening. */
    PULSE_SLOW,
    /** Pulse alpha + slight scale 0.95 ↔ 1.05 a 800ms — thinking. */
    PULSE_SCALE,
    /** Pulse alpha rápido 0.7 ↔ 1.0 a 400ms — speaking. */
    PULSE_FAST,
    /** Shake horizontal ±8dp, 3 ciclos a 300ms — error. */
    SHAKE,
}

/**
 * Transiciones legales del state machine. Cualquier transición no listada se ignora con un log.
 *
 * - IDLE puede ir a cualquier estado activo.
 * - Estados activos pueden volver a IDLE o moverse entre sí.
 * - ERROR es alcanzable desde cualquier estado activo (failsafe).
 */
object BubbleStateTransitions {
    private val LEGAL: Map<BubbleState, Set<BubbleState>> = mapOf(
        BubbleState.IDLE to setOf(BubbleState.LISTENING, BubbleState.SPEAKING, BubbleState.THINKING, BubbleState.ERROR),
        BubbleState.LISTENING to setOf(BubbleState.THINKING, BubbleState.SPEAKING, BubbleState.IDLE, BubbleState.ERROR),
        BubbleState.THINKING to setOf(BubbleState.SPEAKING, BubbleState.LISTENING, BubbleState.IDLE, BubbleState.ERROR),
        BubbleState.SPEAKING to setOf(BubbleState.IDLE, BubbleState.LISTENING, BubbleState.THINKING, BubbleState.ERROR),
        BubbleState.ERROR to setOf(BubbleState.IDLE, BubbleState.LISTENING),
    )

    fun isLegal(from: BubbleState, to: BubbleState): Boolean =
        from == to || (LEGAL[from]?.contains(to) ?: false)
}
