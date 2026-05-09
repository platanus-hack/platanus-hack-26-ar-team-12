package com.beto.app.overlay

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.hypot

object OverlayBubble {

    private const val DRAG_THRESHOLD_DP = 8f
    private const val LONG_PRESS_MS = 600L
    private const val MAGNET_DURATION_MS = 180L
    private const val EDGE_PADDING_DP = 8
    private const val STATUS_BAR_PADDING_DP = 24

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun attach(
        view: View,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val density = view.context.resources.displayMetrics.density
        val dragThresholdPx = DRAG_THRESHOLD_DP * density
        val handler = Handler(Looper.getMainLooper())

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var dragStarted = false
        var longPressFired = false

        val longPressRunnable = Runnable {
            if (!dragStarted) {
                longPressFired = true
                Timber.tag(LogTags.ACCESSIBILITY).i("Bubble long-pressed")
                scope.launch { AgentBus.emit(AgentEvent.BubbleLongPressed) }
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }

        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragStarted = false
                    longPressFired = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragStarted && hypot(dx, dy) > dragThresholdPx) {
                        dragStarted = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (dragStarted) {
                        params.x = (initialX + dx).toInt()
                        params.y = (initialY + dy).toInt()
                        runCatching { windowManager.updateViewLayout(target, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (dragStarted) {
                        magnetToEdge(target, windowManager, params)
                    } else if (!longPressFired) {
                        Timber.tag(LogTags.ACCESSIBILITY).i("Bubble tapped")
                        scope.launch { AgentBus.emit(AgentEvent.BubbleTapped) }
                        target.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun magnetToEdge(
        view: View,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ) {
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(it)
        }
        val density = view.context.resources.displayMetrics.density
        val edgePadding = (EDGE_PADDING_DP * density).toInt()
        val statusBarPadding = (STATUS_BAR_PADDING_DP * density).toInt()
        val viewWidth = view.width.takeIf { it > 0 } ?: (64 * density).toInt()
        val viewHeight = view.height.takeIf { it > 0 } ?: (64 * density).toInt()
        val centerX = params.x + (viewWidth / 2)
        val targetX = if (centerX < metrics.widthPixels / 2) {
            edgePadding
        } else {
            metrics.widthPixels - viewWidth - edgePadding
        }
        val maxY = (metrics.heightPixels - viewHeight - statusBarPadding).coerceAtLeast(statusBarPadding)
        val clampedY = params.y.coerceIn(statusBarPadding, maxY)
        val startX = params.x

        ValueAnimator.ofInt(startX, targetX).apply {
            duration = MAGNET_DURATION_MS
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                params.y = clampedY
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            start()
        }
        Timber.tag(LogTags.ACCESSIBILITY).d(
            "magnet from x=%d to x=%d y=%d",
            startX,
            targetX,
            clampedY,
        )
    }
}
