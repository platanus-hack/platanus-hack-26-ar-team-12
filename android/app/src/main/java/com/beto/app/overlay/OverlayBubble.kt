package com.beto.app.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
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
    private const val CLOSE_ANIMATION_DURATION_MS = 260L
    private const val EDGE_PADDING_DP = 8
    private const val STATUS_BAR_PADDING_DP = 24
    private const val CLOSE_TARGET_HEIGHT_DP = 136
    private const val CLOSE_TARGET_SIZE_DP = 88
    private const val CLOSE_TARGET_WIDTH_RATIO = 0.5f

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
        var closeTargetView: View? = null

        val longPressRunnable = Runnable {
            if (!dragStarted) {
                longPressFired = true
                Timber.tag(LogTags.ACCESSIBILITY).i("Bubble long-pressed")
                hideCloseTarget(windowManager, closeTargetView)
                closeTargetView = null
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
                    closeTargetView = showCloseTarget(view, windowManager, params.type)
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
                        updateCloseTargetState(
                            closeTargetView,
                            isInBottomCenterCloseTarget(target, windowManager, params),
                        )
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (dragStarted) {
                        if (isInBottomCenterCloseTarget(target, windowManager, params)) {
                            Timber.tag(LogTags.ACCESSIBILITY).i("Bubble dropped on close target")
                            target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            animateClose(target, windowManager, params, closeTargetView)
                            closeTargetView = null
                        } else {
                            hideCloseTarget(windowManager, closeTargetView)
                            closeTargetView = null
                            magnetToEdge(target, windowManager, params)
                        }
                    } else if (!longPressFired) {
                        hideCloseTarget(windowManager, closeTargetView)
                        closeTargetView = null
                        Timber.tag(LogTags.ACCESSIBILITY).i("Bubble tapped")
                        scope.launch {
                            AgentBus.emit(AgentEvent.BubbleTapped(SystemClock.elapsedRealtime()))
                        }
                        target.performClick()
                    } else {
                        hideCloseTarget(windowManager, closeTargetView)
                        closeTargetView = null
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showCloseTarget(
        anchor: View,
        windowManager: WindowManager,
        windowType: Int,
    ): View? {
        val density = anchor.context.resources.displayMetrics.density
        val sizePx = (CLOSE_TARGET_SIZE_DP * density).toInt()
        val bottomMarginPx = (EDGE_PADDING_DP * density).toInt()
        val target = FrameLayout(anchor.context).apply {
            alpha = 0f
            scaleX = 0.82f
            scaleY = 0.82f
            elevation = 12f * density
            background = closeTargetBackground(active = false)
            addView(
                TextView(context).apply {
                    text = "X"
                    setTextColor(Color.WHITE)
                    textSize = 34f
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val targetParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = bottomMarginPx
        }

        return try {
            windowManager.addView(target, targetParams)
            target.animate()
                .alpha(0.86f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(MAGNET_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
            target
        } catch (e: RuntimeException) {
            Timber.tag(LogTags.ACCESSIBILITY).w(e, "Close target addView failed")
            null
        }
    }

    private fun updateCloseTargetState(target: View?, active: Boolean) {
        target ?: return
        target.background = closeTargetBackground(active)
        target.animate()
            .alpha(if (active) 1f else 0.86f)
            .scaleX(if (active) 1.12f else 1f)
            .scaleY(if (active) 1.12f else 1f)
            .setDuration(90L)
            .start()
    }

    private fun hideCloseTarget(windowManager: WindowManager, target: View?) {
        target ?: return
        target.animate()
            .alpha(0f)
            .scaleX(0.82f)
            .scaleY(0.82f)
            .setDuration(MAGNET_DURATION_MS)
            .withEndAction {
                runCatching { windowManager.removeView(target) }
            }
            .start()
    }

    private fun closeTargetBackground(active: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Color.parseColor("#D32F2F") else Color.parseColor("#99000000"))
            setStroke(3, Color.WHITE)
        }

    private fun animateClose(
        view: View,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
        closeTargetView: View?,
    ) {
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(it)
        }
        val density = view.context.resources.displayMetrics.density
        val viewWidth = view.width.takeIf { it > 0 } ?: (64 * density).toInt()
        val viewHeight = view.height.takeIf { it > 0 } ?: (64 * density).toInt()
        val startX = params.x
        val startY = params.y
        val targetX = (metrics.widthPixels - viewWidth) / 2
        val targetY = metrics.heightPixels - viewHeight - (EDGE_PADDING_DP * density).toInt()

        view.isEnabled = false
        view.pivotX = viewWidth / 2f
        view.pivotY = viewHeight / 2f

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CLOSE_ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                params.x = (startX + ((targetX - startX) * progress)).toInt()
                params.y = (startY + ((targetY - startY) * progress)).toInt()
                view.alpha = 1f - (0.75f * progress)
                val scale = 1f - (0.55f * progress)
                view.scaleX = scale
                view.scaleY = scale
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hideCloseTarget(windowManager, closeTargetView)
                    scope.launch { AgentBus.emit(AgentEvent.BubbleCloseRequested) }
                }
            })
            start()
        }
    }

    private fun isInBottomCenterCloseTarget(
        view: View,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams,
    ): Boolean {
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(it)
        }
        val density = view.context.resources.displayMetrics.density
        val viewWidth = view.width.takeIf { it > 0 } ?: (64 * density).toInt()
        val viewHeight = view.height.takeIf { it > 0 } ?: (64 * density).toInt()
        val centerX = params.x + (viewWidth / 2)
        val bottomY = params.y + viewHeight
        val closeTargetHalfWidth = (metrics.widthPixels * CLOSE_TARGET_WIDTH_RATIO / 2).toInt()
        val closeTargetTop = metrics.heightPixels - (CLOSE_TARGET_HEIGHT_DP * density).toInt()
        val screenCenterX = metrics.widthPixels / 2

        return centerX in (screenCenterX - closeTargetHalfWidth)..(screenCenterX + closeTargetHalfWidth) &&
            bottomY >= closeTargetTop
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
