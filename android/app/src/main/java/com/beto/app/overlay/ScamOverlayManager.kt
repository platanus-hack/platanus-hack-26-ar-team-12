package com.beto.app.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import com.beto.app.BetoApplication
import com.beto.app.scam.RiskAssessment
import com.beto.app.trust.TrustedCallIntents
import com.beto.app.trust.TrustedContact
import com.beto.app.trust.TrustedContactActivity
import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * Singleton que muestra/oculta el overlay del Escudo Antiestafas sobre cualquier app.
 *
 * Se renderiza con `TYPE_ACCESSIBILITY_OVERLAY` cuando el AccessibilityService está conectado
 * (lo está siempre que el Escudo Antiestafas pueda activarse — sin Accessibility no hay
 * detección, así que es una garantía hard). Fallback a `TYPE_APPLICATION_OVERLAY` por las dudas.
 *
 * NO consume eventos del bus directamente — la decisión de qué/cuándo mostrar la toma
 * `BetoForegroundService` (que escucha `AgentEvent.ScamRiskDetected`). Esto mantiene al manager
 * agnóstico y testeable.
 */
object ScamOverlayManager {

    private var overlayView: View? = null
    @Volatile private var currentContextHash: String? = null

    /** Snapshot del contextHash actualmente mostrado — null si no hay overlay visible. */
    fun visibleContextHash(): String? = currentContextHash

    fun isVisible(): Boolean = overlayView != null

    /**
     * Muestra el overlay para una assessment específica. Si ya hay otro overlay visible:
     *  - Si el `contextHash` es el mismo → no-op (no parpadea).
     *  - Si es distinto → reemplaza el contenido sin re-attach al WindowManager (evita flicker).
     */
    fun show(
        context: Context,
        assessment: RiskAssessment,
        contextHash: String,
        bodyOverride: String? = null,
    ) {
        if (!Settings.canDrawOverlays(context)) {
            Timber.tag(LogTags.ACCESSIBILITY).w("ScamOverlay show but canDrawOverlays=false")
            return
        }
        if (currentContextHash == contextHash && overlayView != null) {
            Timber.tag(LogTags.ACCESSIBILITY).d("ScamOverlay show same hash — no-op")
            return
        }

        val appContext = context.applicationContext
        val trustedContact = runCatching { BetoApplication.trustedContactsRepository.current() }
            .getOrNull()

        if (overlayView != null) {
            Timber.tag(LogTags.ACCESSIBILITY).i(
                "ScamOverlay rebind hash=%s -> %s",
                currentContextHash?.take(8),
                contextHash.take(8),
            )
            currentContextHash = contextHash
            ScamAlertOverlay.wrap(overlayView!!).bind(
                assessment = assessment,
                trustedContact = trustedContact,
                bodyOverride = bodyOverride,
                onCall = { handleCall(appContext, trustedContact) },
                onDismiss = { handleDismiss(appContext) },
                onAcknowledge = { handleAcknowledge(appContext) },
            )
            return
        }

        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: run {
                Timber.tag(LogTags.ACCESSIBILITY).e("WindowManager unavailable for ScamOverlay")
                return
            }

        val overlay = ScamAlertOverlay.inflate(appContext)
        overlay.bind(
            assessment = assessment,
            trustedContact = trustedContact,
            bodyOverride = bodyOverride,
            onCall = { handleCall(appContext, trustedContact) },
            onDismiss = { handleDismiss(appContext) },
            onAcknowledge = { handleAcknowledge(appContext) },
        )

        val params = buildParams(appContext)

        try {
            windowManager.addView(overlay.root, params)
            overlayView = overlay.root
            currentContextHash = contextHash
            Timber.tag(LogTags.ACCESSIBILITY).i(
                "ScamOverlay shown signals=%s hash=%s",
                assessment.signals.map { it.name },
                contextHash.take(8),
            )
        } catch (t: Throwable) {
            Timber.tag(LogTags.ACCESSIBILITY).e(t, "ScamOverlay addView failed")
        }
    }

    /**
     * Reemplaza el texto del cuerpo del overlay si todavía está visible y corresponde al
     * mismo `contextHash`. Lo usa el flujo del LLM Explainer (Block 5): el overlay aparece
     * con frase canned, y cuando el LLM responde swappeamos in-place.
     *
     * Si el overlay ya se cerró, o el hash cambió (otro mensaje estafador entró antes), no-op.
     */
    fun updateBody(contextHash: String, body: String) {
        if (body.isBlank()) return
        val view = overlayView ?: return
        if (currentContextHash != contextHash) {
            Timber.tag(LogTags.ACCESSIBILITY).d(
                "ScamOverlay updateBody stale hash=%s current=%s",
                contextHash.take(8),
                currentContextHash?.take(8),
            )
            return
        }
        ScamAlertOverlay.wrap(view).setBody(body)
        Timber.tag(LogTags.ACCESSIBILITY).d("ScamOverlay body updated len=%d", body.length)
    }

    fun hide(context: Context) {
        val view = overlayView ?: return
        val windowManager = context.applicationContext
            .getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return
        try {
            windowManager.removeView(view)
            Timber.tag(LogTags.ACCESSIBILITY).i("ScamOverlay removed")
        } catch (t: Throwable) {
            Timber.tag(LogTags.ACCESSIBILITY).w(t, "ScamOverlay removeView failed")
        } finally {
            overlayView = null
            currentContextHash = null
        }
    }

    private fun handleCall(context: Context, trustedContact: TrustedContact?) {
        if (trustedContact != null) {
            TrustedCallIntents.call(context, trustedContact)
        } else {
            // No hay contacto configurado → llevamos al user al setup. Es 1 tap más, pero no
            // queremos un botón "muerto" en una alerta de estafa.
            Timber.tag(LogTags.INTENT).i("ScamOverlay no trusted contact -> opening picker")
            context.startActivity(
                Intent(context, TrustedContactActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        hide(context)
    }

    private fun handleDismiss(context: Context) {
        Timber.tag(LogTags.ACCESSIBILITY).i("ScamOverlay user chose 'no responder'")
        hide(context)
    }

    private fun handleAcknowledge(context: Context) {
        Timber.tag(LogTags.ACCESSIBILITY).i("ScamOverlay user acknowledged")
        hide(context)
    }

    private fun buildParams(context: Context): WindowManager.LayoutParams {
        val type = pickOverlayType(context)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // FLAG_NOT_FOCUSABLE para no robar el foco al chat. Sin TOUCH_MODAL para que
            // los taps en el scrim no rompan la app de fondo (aunque el scrim no es interactivo).
            // FLAG_LAYOUT_IN_SCREEN para ocupar toda la pantalla incluida la status bar.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        }
    }

    private fun pickOverlayType(context: Context): Int {
        val connected = isAccessibilityServiceConnected(context)
        return if (connected) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
    }

    private fun isAccessibilityServiceConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return manager.getEnabledAccessibilityServiceList(0).orEmpty().any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }
}
