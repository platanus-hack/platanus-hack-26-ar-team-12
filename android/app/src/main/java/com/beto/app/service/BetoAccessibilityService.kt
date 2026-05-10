package com.beto.app.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.util.LogTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class BetoAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.tag(LogTags.ACCESSIBILITY).i("BetoAccessibilityService connected")
        instance = this
        scope.launch { AgentBus.emit(AgentEvent.ServiceStarted) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        Timber.tag(LogTags.ACCESSIBILITY).v(
            "event type=%s package=%s",
            event.eventType,
            event.packageName,
        )
    }

    override fun onInterrupt() {
        Timber.tag(LogTags.ACCESSIBILITY).w("BetoAccessibilityService interrupted")
    }

    override fun onDestroy() {
        Timber.tag(LogTags.ACCESSIBILITY).i("BetoAccessibilityService destroyed")
        if (instance === this) instance = null
        scope.launch { AgentBus.emit(AgentEvent.ServiceStopped) }
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Busca un nodo cuyo `text` contenga (case-insensitive) la query. BFS sobre
     * `rootInActiveWindow` con cap de 200 nodos para evitar trees patológicos.
     * Phase 4-04 lo usa para localizar el View target del Modo Guía.
     */
    fun findNodeByText(query: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return bfsFind(root) { node ->
            val text = node.text?.toString().orEmpty()
            text.equals(query, ignoreCase = true) ||
                text.contains(query, ignoreCase = true)
        }
    }

    /**
     * Busca un nodo por contentDescription (más confiable que text para íconos
     * — apps como WhatsApp setean contentDescription explícito en botones de mic / video).
     */
    fun findNodeByContentDescription(query: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return bfsFind(root) { node ->
            val desc = node.contentDescription?.toString().orEmpty()
            desc.equals(query, ignoreCase = true) ||
                desc.contains(query, ignoreCase = true)
        }
    }

    /** Bounds en coordenadas de pantalla del nodo. Util para `GestureOverlay.pointTo`. */
    fun nodeBoundsInScreen(node: AccessibilityNodeInfo): Rect =
        Rect().also { node.getBoundsInScreen(it) }

    private fun bfsFind(
        root: AccessibilityNodeInfo,
        cap: Int = MAX_NODES,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < cap) {
            val node = queue.removeFirst()
            visited++
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: BetoAccessibilityService? = null
            private set

        private const val MAX_NODES = 200
    }
}
