package com.beto.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import com.beto.app.R
import com.beto.app.util.LogTags
import timber.log.Timber

object OverlayManager {

    private var bubbleView: View? = null

    fun show(context: Context) {
        if (bubbleView != null) {
            Timber.tag(LogTags.ACCESSIBILITY).d("OverlayManager.show already visible")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Timber.tag(LogTags.ACCESSIBILITY).w("show() but canDrawOverlays=false")
            return
        }

        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: run {
                Timber.tag(LogTags.ACCESSIBILITY).e("WindowManager unavailable")
                return
            }
        val view = LayoutInflater.from(appContext).inflate(R.layout.overlay_bubble, null, false)
        val params = computeInitialParams(appContext, windowManager)
        OverlayBubble.attach(view, windowManager, params)

        try {
            windowManager.addView(view, params)
            bubbleView = view
            Timber.tag(LogTags.ACCESSIBILITY).i(
                "Bubble shown type=%s pos=(%d,%d)",
                windowTypeName(params.type),
                params.x,
                params.y,
            )
        } catch (e: RuntimeException) {
            Timber.tag(LogTags.ACCESSIBILITY).e(e, "addView failed")
        }
    }

    fun hide(context: Context) {
        val view = bubbleView ?: return
        val windowManager =
            context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return

        try {
            windowManager.removeView(view)
            Timber.tag(LogTags.ACCESSIBILITY).i("Bubble removed")
        } catch (e: RuntimeException) {
            Timber.tag(LogTags.ACCESSIBILITY).w(e, "removeView failed")
        } finally {
            bubbleView = null
        }
    }

    private fun computeInitialParams(
        context: Context,
        windowManager: WindowManager,
    ): WindowManager.LayoutParams {
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(it)
        }
        val sizePx = (BUBBLE_DIAMETER_DP * metrics.density).toInt()
        val paddingPx = (EDGE_PADDING_DP * metrics.density).toInt()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            pickOverlayType(context),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - sizePx - paddingPx
            y = (metrics.heightPixels / 2) - (sizePx / 2)
        }
    }

    private fun pickOverlayType(context: Context): Int {
        val connected = isAccessibilityServiceConnected(context)
        val type = if (connected) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        Timber.tag(LogTags.ACCESSIBILITY).d(
            "pickOverlayType connected=%s -> %s",
            connected,
            windowTypeName(type),
        )
        return type
    }

    private fun isAccessibilityServiceConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return manager.getEnabledAccessibilityServiceList(0).orEmpty().any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    private fun windowTypeName(type: Int): String = when (type) {
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY -> "APPLICATION_OVERLAY"
        else -> "type_$type"
    }

    private const val BUBBLE_DIAMETER_DP = 64
    private const val EDGE_PADDING_DP = 8
}
