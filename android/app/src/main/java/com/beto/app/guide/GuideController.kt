package com.beto.app.guide

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.service.BetoAccessibilityService
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Orquestador del Modo Guía. Para cada step del script:
 *  1. Localiza el View target via `BetoAccessibilityService.findNodeByText / ByContentDescription`.
 *     Reintenta 1x con delay si no lo encuentra (puede ser que la pantalla aún esté cargando).
 *  2. Saca los bounds del nodo y muestra la flecha en `GestureOverlay`.
 *  3. Lee el `voiceText` con `TtsManager.speakAndAwait` (suspend hasta que termine la frase).
 *  4. Espera 3s para que el user procese, luego pasa al siguiente step.
 *
 * Cleanup en finally: `GestureOverlay.hide()` siempre se llama, aún si una excepción
 * sube. El usuario puede cancelar tocando la burbuja → emite `GuideCancelled`.
 */
class GuideController(
    private val context: Context,
    private val accessibilityService: () -> BetoAccessibilityService? = { BetoAccessibilityService.instance },
    private val gestureOverlay: GestureController = DefaultGestureController(context),
    private val tts: TtsSpeaker = TtsSpeakerImpl,
) {

    private var activeJob: Job? = null

    fun start(action: GuideAction, scope: CoroutineScope) {
        cancel()
        activeJob = scope.launch { runScript(action) }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        gestureOverlay.hide()
    }

    private suspend fun runScript(action: GuideAction) {
        val script = GuideScripts.forAction(action)
        Timber.tag(LogTags.ACCESSIBILITY).i("GUIDE_STARTED action=%s", action)
        AgentBus.emit(AgentEvent.GuideStarted(action))

        try {
            val service = accessibilityService() ?: run {
                tts.speakAndAwait("No tengo permiso de accesibilidad. No puedo guiarte ahora.")
                return
            }

            // Abrir app target si hace falta
            if (script.intentKind == IntentKind.OPEN_APP && script.appPackage != null) {
                openAppIfNeeded(script.appPackage)
                delay(APP_LOAD_DELAY_MS)
            }

            for ((index, step) in script.steps.withIndex()) {
                Timber.tag(LogTags.ACCESSIBILITY).d("GUIDE_STEP step=%d/%d", index + 1, script.steps.size)
                val node = findTargetWithRetry(service, step.target)
                if (node == null) {
                    Timber.tag(LogTags.ACCESSIBILITY).w("GUIDE_TARGET_NOT_FOUND step=%d", index + 1)
                    tts.speakAndAwait(NOT_FOUND_PHRASE)
                    return
                }

                val bounds = service.nodeBoundsInScreen(node)
                gestureOverlay.show(bounds)
                AgentBus.emit(AgentEvent.GuideStepShown(action, index + 1))
                tts.speakAndAwait(step.voiceText)
                delay(STEP_PAUSE_MS)
                gestureOverlay.hide()
            }

            tts.speakAndAwait(CLOSING_PHRASE)
        } catch (e: CancellationException) {
            Timber.tag(LogTags.ACCESSIBILITY).i("Guide cancelled")
            AgentBus.emit(AgentEvent.GuideCancelled(action))
            throw e
        } catch (e: Exception) {
            Timber.tag(LogTags.ACCESSIBILITY).e(e, "Guide failed unexpectedly")
            tts.speakAndAwait(NOT_FOUND_PHRASE)
        } finally {
            gestureOverlay.hide()
            AgentBus.emit(AgentEvent.GuideEnded(action))
        }
    }

    private fun openAppIfNeeded(packageName: String) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: run {
                Timber.tag(LogTags.ACCESSIBILITY).w("App not installed: %s", packageName)
                return
            }
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.tag(LogTags.ACCESSIBILITY).w(it, "Failed to open app %s", packageName) }
    }

    private suspend fun findTargetWithRetry(
        service: BetoAccessibilityService,
        target: TargetSelector,
    ): AccessibilityNodeInfo? {
        repeat(MAX_FIND_ATTEMPTS) { attempt ->
            val node = when (target) {
                is TargetSelector.ByText -> service.findNodeByText(target.text)
                is TargetSelector.ByContentDescription -> service.findNodeByContentDescription(target.description)
            }
            if (node != null) return node
            if (attempt < MAX_FIND_ATTEMPTS - 1) delay(FIND_RETRY_DELAY_MS)
        }
        return null
    }

    companion object {
        private const val MAX_FIND_ATTEMPTS = 2
        private const val FIND_RETRY_DELAY_MS = 1_500L
        private const val APP_LOAD_DELAY_MS = 1_500L
        private const val STEP_PAUSE_MS = 3_000L
        private const val NOT_FOUND_PHRASE = "No encuentro lo que buscás. Probá abrir la app primero."
        private const val CLOSING_PHRASE = "Listo, eso es todo. Probá vos."
    }
}

/** Abstracción para testear sin Android: encapsula `GestureOverlayManager.show/hide`. */
interface GestureController {
    fun show(targetBounds: android.graphics.Rect)
    fun hide()
}

private class DefaultGestureController(private val context: Context) : GestureController {
    override fun show(targetBounds: android.graphics.Rect) = GestureOverlayManager.show(context, targetBounds)
    override fun hide() = GestureOverlayManager.hide()
}

/** Abstracción para testear sin TtsManager singleton. */
interface TtsSpeaker {
    suspend fun speakAndAwait(text: String)
}

private object TtsSpeakerImpl : TtsSpeaker {
    override suspend fun speakAndAwait(text: String) = TtsManager.speakAndAwait(text)
}
