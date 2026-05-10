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
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Singleton que gestiona la `OverlayBubble`.
 *
 * - Crea la View, la registra en `WindowManager`.
 * - Conecta el `BubbleStateController` (Phase 4-02) y consume `AgentEvent`s para correr
 *   la máquina de estados visual de la burbuja.
 * - Auto-recovery: timeout de LISTENING (30s) y clear de ERROR (1.5s) → vuelve a IDLE.
 */
object OverlayManager {

    private var bubbleView: View? = null
    private var stateController: BubbleStateController? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var eventsJob: Job? = null
    private var listeningTimeoutJob: Job? = null
    private var errorClearJob: Job? = null

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
            stateController = view.bubbleStateController().also { it.applyImmediate(BubbleState.IDLE) }
            startEventListener()
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
            stopEventListener()
            stateController = null
            bubbleView = null
        }
    }

    private fun startEventListener() {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            AgentBus.events.collect { event -> handleEvent(event) }
        }
    }

    private fun stopEventListener() {
        eventsJob?.cancel()
        eventsJob = null
        listeningTimeoutJob?.cancel()
        listeningTimeoutJob = null
        errorClearJob?.cancel()
        errorClearJob = null
    }

    private fun handleEvent(event: AgentEvent) {
        when (event) {
            // BubbleTapped suele preceder a VoiceCaptureStarted; transicionamos optimistic
            // para feedback inmediato.
            is AgentEvent.BubbleTapped -> transitionTo(BubbleState.LISTENING)
            AgentEvent.VoiceCaptureStarted -> transitionTo(BubbleState.LISTENING)
            is AgentEvent.VoiceCaptured -> transitionTo(BubbleState.THINKING)
            is AgentEvent.SttCorrectionStarted -> transitionTo(BubbleState.THINKING)
            AgentEvent.VoiceCaptureTimeout -> transitionTo(BubbleState.ERROR)
            is AgentEvent.VoiceCaptureFailed -> transitionTo(BubbleState.ERROR)
            is AgentEvent.TtsStarted -> transitionTo(BubbleState.SPEAKING)
            is AgentEvent.TtsSpoke -> {
                // utterance completó → si seguimos en SPEAKING, volvemos a IDLE.
                if (stateController?.current() == BubbleState.SPEAKING) {
                    transitionTo(BubbleState.IDLE)
                }
            }
            is AgentEvent.TtsFailed -> transitionTo(BubbleState.ERROR)
            is AgentEvent.IntentLaunched -> {
                // El Intent se disparó. La burbuja queda en SPEAKING durante el TTS de éxito.
            }
            is AgentEvent.ToolFailed -> transitionTo(BubbleState.ERROR)
            else -> Unit
        }
    }

    private fun transitionTo(state: BubbleState) {
        val controller = stateController ?: return
        if (!controller.apply(state)) return

        // Auto-recovery por estado
        listeningTimeoutJob?.cancel()
        errorClearJob?.cancel()
        when (state) {
            BubbleState.LISTENING -> startListeningTimeout()
            BubbleState.ERROR -> scheduleErrorClear()
            else -> Unit
        }
    }

    private fun startListeningTimeout() {
        listeningTimeoutJob = scope.launch {
            delay(LISTENING_TIMEOUT_MS)
            AgentBus.emit(AgentEvent.VoiceCaptureTimeout)
        }
    }

    private fun scheduleErrorClear() {
        errorClearJob = scope.launch {
            delay(ERROR_CLEAR_MS)
            stateController?.apply(BubbleState.IDLE)
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
            sizePx,
            sizePx,
            pickOverlayType(context),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
    private const val LISTENING_TIMEOUT_MS = 30_000L
    private const val ERROR_CLEAR_MS = 1_500L
}
