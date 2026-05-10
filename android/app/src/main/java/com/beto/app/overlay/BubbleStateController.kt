package com.beto.app.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.beto.app.R
import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * Aplica visualmente un `BubbleState` a las vistas de la burbuja.
 *
 * - Tinta el anillo (`ring_view.backgroundTintList`).
 * - Cambia el ícono de estado en el badge bottom-right (oculto en IDLE).
 * - Lanza/cancela la animación correspondiente.
 *
 * Honra `Settings.Global.ANIMATOR_DURATION_SCALE = 0` (accessibility motion off):
 * en ese caso no corre animaciones — solo cambia color e ícono. Esto cubre la guideline
 * de la skill ui-ux-pro-max #99 (Motion Sensitivity / prefers-reduced-motion).
 */
class BubbleStateController(
    private val ringView: View,
    private val iconContainer: View,
    private val iconView: ImageView,
) {
    private var currentState: BubbleState = BubbleState.IDLE
    private var runningAnimator: android.animation.Animator? = null

    fun applyImmediate(state: BubbleState) {
        currentState = state
        renderStaticState(state)
        runningAnimator?.cancel()
        runningAnimator = null
    }

    fun apply(state: BubbleState): Boolean {
        if (!BubbleStateTransitions.isLegal(currentState, state)) {
            Timber.tag(LogTags.ACCESSIBILITY).w(
                "Illegal bubble state transition %s -> %s — ignoring",
                currentState,
                state,
            )
            return false
        }
        if (state == currentState) return false

        Timber.tag(LogTags.ACCESSIBILITY).d("BUBBLE_STATE_CHANGED from=%s to=%s", currentState, state)
        currentState = state
        renderStaticState(state)
        runAnimation(state.animation)
        return true
    }

    fun current(): BubbleState = currentState

    private fun renderStaticState(state: BubbleState) {
        val ctx = ringView.context
        val color = ContextCompat.getColor(ctx, state.ringColorRes)
        ringView.backgroundTintList = ColorStateList.valueOf(color)

        val icon = state.centerIconRes
        if (icon == null) {
            iconContainer.visibility = View.GONE
        } else {
            iconView.setImageResource(icon)
            iconView.imageTintList = ColorStateList.valueOf(color)
            iconContainer.visibility = View.VISIBLE
        }
    }

    private fun runAnimation(animation: BubbleAnimation) {
        runningAnimator?.cancel()
        runningAnimator = null

        // Reset cualquier transformación residual antes de aplicar la nueva
        ringView.alpha = 1f
        ringView.scaleX = 1f
        ringView.scaleY = 1f
        ringView.translationX = 0f

        if (animation == BubbleAnimation.NONE || isMotionDisabled(ringView.context)) return

        runningAnimator = when (animation) {
            BubbleAnimation.PULSE_SLOW -> alphaPulse(durationMs = PULSE_SLOW_MS)
            BubbleAnimation.PULSE_SCALE -> alphaScalePulse()
            BubbleAnimation.PULSE_FAST -> alphaPulse(durationMs = PULSE_FAST_MS)
            BubbleAnimation.SHAKE -> shake()
            BubbleAnimation.NONE -> null
        }?.apply { start() }
    }

    private fun alphaPulse(durationMs: Long): ObjectAnimator =
        ObjectAnimator.ofFloat(ringView, "alpha", 0.65f, 1.0f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

    private fun alphaScalePulse(): AnimatorSet {
        val alpha = ObjectAnimator.ofFloat(ringView, "alpha", 0.7f, 1.0f).apply {
            duration = PULSE_SLOW_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleX = ObjectAnimator.ofFloat(ringView, "scaleX", 0.95f, 1.05f).apply {
            duration = PULSE_SLOW_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(ringView, "scaleY", 0.95f, 1.05f).apply {
            duration = PULSE_SLOW_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        return AnimatorSet().apply { playTogether(alpha, scaleX, scaleY) }
    }

    private fun shake(): ObjectAnimator {
        val displacementPx = SHAKE_DP * ringView.context.resources.displayMetrics.density
        return ObjectAnimator.ofFloat(
            ringView,
            "translationX",
            0f, -displacementPx, displacementPx, -displacementPx, displacementPx, 0f,
        ).apply {
            duration = SHAKE_MS
            repeatCount = 0
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun isMotionDisabled(context: Context): Boolean {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return scale == 0f
    }

    companion object {
        private const val PULSE_SLOW_MS = 800L
        private const val PULSE_FAST_MS = 400L
        private const val SHAKE_MS = 320L
        private const val SHAKE_DP = 8f
    }
}

/**
 * Helper que crea un `BubbleStateController` apuntando a las views por id del layout
 * `overlay_bubble.xml`. Usado por `OverlayManager.show()`.
 */
fun View.bubbleStateController(): BubbleStateController = BubbleStateController(
    ringView = findViewById(R.id.bubble_ring),
    iconContainer = findViewById(R.id.bubble_state_icon_container),
    iconView = findViewById(R.id.bubble_state_icon),
)
