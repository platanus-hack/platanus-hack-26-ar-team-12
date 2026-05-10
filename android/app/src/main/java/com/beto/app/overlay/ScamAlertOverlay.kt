package com.beto.app.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.beto.app.R
import com.beto.app.scam.RiskAssessment
import com.beto.app.scam.Signal
import com.beto.app.trust.TrustedContact

/**
 * Wrapper que infla `scam_alert_overlay.xml` y expone hooks para personalizar:
 *  - Texto del cuerpo (frase warm — Block 5 lo va a sustituir con LLM Explainer).
 *  - Chips dinámicos por Signal detectada.
 *  - Texto y handler del botón "Llamar" según haya o no `TrustedContact` configurado.
 */
class ScamAlertOverlay private constructor(val root: View) {

    private val titleView: TextView = root.findViewById(R.id.scam_alert_title)
    private val bodyView: TextView = root.findViewById(R.id.scam_alert_body)
    private val signalsContainer: LinearLayout = root.findViewById(R.id.scam_alert_signals)
    private val callButton: TextView = root.findViewById(R.id.scam_alert_btn_call)
    private val dismissButton: TextView = root.findViewById(R.id.scam_alert_btn_dismiss)
    private val acknowledgeButton: TextView = root.findViewById(R.id.scam_alert_btn_acknowledge)

    fun bind(
        assessment: RiskAssessment,
        trustedContact: TrustedContact?,
        bodyOverride: String? = null,
        onCall: () -> Unit,
        onDismiss: () -> Unit,
        onAcknowledge: () -> Unit,
    ) {
        bodyView.text = bodyOverride ?: defaultBodyFor(assessment)
        renderSignalChips(assessment)
        renderCallButton(trustedContact)
        callButton.setOnClickListener { onCall() }
        dismissButton.setOnClickListener { onDismiss() }
        acknowledgeButton.setOnClickListener { onAcknowledge() }
    }

    fun setBody(text: String) {
        bodyView.text = text
    }

    private fun defaultBodyFor(assessment: RiskAssessment): String {
        // Fallback canned por combinación de signals — el LLM Explainer (Block 5) lo va a
        // mejorar contextualmente. Mantenemos el tono cálido del CLAUDE.md.
        val signals = assessment.signals.toSet()
        return when {
            Signal.IMPERSONATION_FAMILY in signals && Signal.NEW_NUMBER in signals ->
                "Te están pidiendo cosas como si fueran familia, pero el número es nuevo. Mejor frenemos."
            Signal.MONEY_REQUEST in signals && Signal.URGENCY in signals ->
                "Te están pidiendo plata con urgencia. Pará un segundo, mejor confirmá con alguien."
            Signal.AUTHORITY_IMPERSONATION in signals ->
                "Esto se hace pasar por una entidad oficial. AFIP y ANSES nunca piden datos así."
            Signal.REMOTE_CONTROL in signals ->
                "Te están pidiendo instalar algo para 'ayudarte'. Eso es señal de estafa."
            Signal.PRIZE_BAIT in signals ->
                "Te dicen que ganaste algo. Si no jugaste, es casi siempre un engaño."
            else ->
                "Mejor frenemos un segundo antes de responder."
        }
    }

    private fun renderSignalChips(assessment: RiskAssessment) {
        signalsContainer.removeAllViews()
        val ctx = signalsContainer.context
        val density = ctx.resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (8 * density).toInt()
        val gap = (8 * density).toInt()

        val seen = LinkedHashSet<Signal>()
        for (hit in assessment.hits) {
            if (!seen.add(hit.signal)) continue
            val chip = TextView(ctx).apply {
                text = hit.signal.chipLabel
                textSize = 13f
                setBackgroundResource(R.drawable.bg_scam_signal_chip)
                setTextColor(ctx.getColor(R.color.scam_alert_signal_text))
                setPadding(padH, padV, padH, padV)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { rightMargin = gap }
                layoutParams = lp
            }
            signalsContainer.addView(chip)
        }
    }

    private fun renderCallButton(trustedContact: TrustedContact?) {
        callButton.text = trustedContact?.callLabel ?: "Llamar a alguien de confianza"
    }

    companion object {
        fun inflate(context: Context, parent: ViewGroup? = null): ScamAlertOverlay {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.scam_alert_overlay, parent, false)
            return ScamAlertOverlay(view)
        }

        /** Re-envuelve un View ya inflado y attached (uso: re-bind sin re-inflar). */
        fun wrap(view: View): ScamAlertOverlay = ScamAlertOverlay(view)
    }
}
