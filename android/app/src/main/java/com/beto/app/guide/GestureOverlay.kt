package com.beto.app.guide

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.beto.app.R
import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * View que dibuja una flecha animada apuntando a un punto de la pantalla.
 *
 * Animación: pulse alpha + bounce vertical. Layout absoluto via WindowManager.LayoutParams
 * con `FLAG_NOT_TOUCHABLE` — los toques pasan a la app debajo (intencional: queremos que
 * el user toque el target real, no la flecha).
 */
class GestureOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val arrow: ImageView = ImageView(context).apply {
        setImageResource(R.drawable.guide_arrow)
        layoutParams = LayoutParams(
            ARROW_SIZE_DP.dp(),
            ARROW_SIZE_DP.dp(),
            Gravity.CENTER,
        )
    }

    private var runningAnimator: Animator? = null

    init {
        addView(arrow)
    }

    fun startBounceAnimation() {
        runningAnimator?.cancel()
        val bounce = ObjectAnimator.ofFloat(arrow, "translationY", 0f, -BOUNCE_DP.dp().toFloat()).apply {
            duration = BOUNCE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val alpha = ObjectAnimator.ofFloat(arrow, "alpha", 0.6f, 1.0f).apply {
            duration = BOUNCE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        runningAnimator = AnimatorSet().apply {
            playTogether(bounce, alpha)
            start()
        }
    }

    fun stopAnimation() {
        runningAnimator?.cancel()
        runningAnimator = null
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARROW_SIZE_DP = 56
        private const val BOUNCE_DP = 8
        private const val BOUNCE_DURATION_MS = 600L
    }
}

/**
 * Singleton que registra/elimina la `GestureOverlay` via WindowManager.
 *
 * Cleanup garantizado en `hide()` y en exception paths del `GuideController`.
 */
object GestureOverlayManager {

    private var overlayView: GestureOverlay? = null
    private var attachedTo: WindowManager? = null

    fun show(context: Context, targetBounds: Rect) {
        val windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: run {
                Timber.tag(LogTags.ACCESSIBILITY).e("GestureOverlay: WindowManager unavailable")
                return
            }

        // Si ya hay overlay pegado, removelo limpio antes de recrear
        hide()

        val overlay = GestureOverlay(context.applicationContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            preferAccessibilityType(context),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Posicionar la flecha justo arriba del target, apuntando hacia abajo
            x = targetBounds.centerX() - (ARROW_SIZE_DP.dpIn(context) / 2)
            y = (targetBounds.top - ARROW_SIZE_DP.dpIn(context) - ARROW_GAP_DP.dpIn(context)).coerceAtLeast(0)
        }

        try {
            windowManager.addView(overlay, params)
            overlay.startBounceAnimation()
            overlayView = overlay
            attachedTo = windowManager
            Timber.tag(LogTags.ACCESSIBILITY).i(
                "GUIDE_OVERLAY_SHOWN target=(%d,%d,%d,%d)",
                targetBounds.left, targetBounds.top, targetBounds.right, targetBounds.bottom,
            )
        } catch (e: RuntimeException) {
            Timber.tag(LogTags.ACCESSIBILITY).e(e, "GestureOverlay addView failed")
            overlayView = null
            attachedTo = null
        }
    }

    fun hide() {
        val view = overlayView ?: return
        val wm = attachedTo
        view.stopAnimation()
        if (wm != null) {
            runCatching { wm.removeView(view) }
                .onFailure { Timber.tag(LogTags.ACCESSIBILITY).w(it, "GestureOverlay removeView failed") }
        }
        overlayView = null
        attachedTo = null
        Timber.tag(LogTags.ACCESSIBILITY).i("GUIDE_OVERLAY_HIDDEN")
    }

    private fun preferAccessibilityType(context: Context): Int {
        // Si el AccessibilityService está conectado, podemos usar TYPE_ACCESSIBILITY_OVERLAY
        // que tiene prioridad sobre apps. Sino, fallback a APPLICATION_OVERLAY (requires SYSTEM_ALERT_WINDOW).
        val a11yEnabled = (context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? android.view.accessibility.AccessibilityManager)
            ?.getEnabledAccessibilityServiceList(0).orEmpty()
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
        return if (a11yEnabled) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private fun Int.dpIn(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    private const val ARROW_SIZE_DP = 56
    private const val ARROW_GAP_DP = 12
}
