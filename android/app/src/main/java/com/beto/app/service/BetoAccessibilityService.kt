package com.beto.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
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
        scope.launch { AgentBus.emit(AgentEvent.ServiceStarted) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        Timber.tag(LogTags.ACCESSIBILITY).v(
            "event type=%s packagePresent=%s",
            event.eventType,
            event.packageName != null,
        )
    }

    override fun onInterrupt() {
        Timber.tag(LogTags.ACCESSIBILITY).w("BetoAccessibilityService interrupted")
    }

    override fun onDestroy() {
        Timber.tag(LogTags.ACCESSIBILITY).i("BetoAccessibilityService destroyed")
        scope.launch { AgentBus.emit(AgentEvent.ServiceStopped) }
        scope.cancel()
        super.onDestroy()
    }
}
