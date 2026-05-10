package com.beto.app.scam

import java.security.MessageDigest

/**
 * Orquesta el pipeline proactivo:
 *   AccessibilityEvent (texto crudo) → buffer → engine → throttle/dedupe/cooldown → decisión.
 *
 * Pure Kotlin para que sea unit-testeable sin Android. Quien lo invoca (BetoAccessibilityService)
 * convierte la `Decision.Emit` en un `AgentEvent.ScamRiskDetected` sobre el AgentBus.
 *
 * Reglas (calibradas al pitch):
 * - Solo evalúa si el packageName está en la whitelist.
 * - Throttle: ignora invocaciones más rápidas que `minIntervalBetweenAssessmentsMs` por package.
 * - Cooldown: tras emitir HIGH, ignora N ms sobre el mismo package.
 * - Dedupe: si el `contextHash` (sha1 de la ventana del buffer) ya disparó una alerta reciente,
 *   no re-emite — evita spam cuando el user scrollea o el chat repinta.
 */
class ScamWatcher(
    private val engine: ScamRiskEngine = ScamRiskEngine(),
    private val buffer: ScamMessageBuffer = ScamMessageBuffer(),
    private val whitelist: Set<String> = ScamPackages.DEFAULT_WHITELIST,
    private val minIntervalBetweenAssessmentsMs: Long = MIN_INTERVAL_MS,
    private val cooldownAfterEmitMs: Long = COOLDOWN_MS,
    private val emitOnLevels: Set<RiskLevel> = setOf(RiskLevel.HIGH),
) {

    private val lastAssessmentAtMs = HashMap<String, Long>()
    private val lastEmitAtMs = HashMap<String, Long>()
    private val lastEmittedHashByPackage = HashMap<String, String>()

    sealed class Decision {
        object Ignored : Decision()
        object Throttled : Decision()
        object Cooldown : Decision()
        object Deduped : Decision()
        data class BelowThreshold(val assessment: RiskAssessment) : Decision()
        data class Emit(
            val packageName: String,
            val assessment: RiskAssessment,
            val text: String,
            val contextHash: String,
        ) : Decision()
    }

    @Synchronized
    fun observe(packageName: String, rawText: String, nowMs: Long): Decision {
        if (packageName !in whitelist) return Decision.Ignored
        if (rawText.isBlank()) return Decision.Ignored

        val lastEmit = lastEmitAtMs[packageName]
        if (lastEmit != null && nowMs - lastEmit < cooldownAfterEmitMs) {
            buffer.append(packageName, rawText)
            return Decision.Cooldown
        }

        val window = buffer.append(packageName, rawText)
        if (window.isBlank()) return Decision.Ignored

        val lastAt = lastAssessmentAtMs[packageName]
        if (lastAt != null && nowMs - lastAt < minIntervalBetweenAssessmentsMs) return Decision.Throttled
        lastAssessmentAtMs[packageName] = nowMs

        val assessment = engine.assess(window)
        if (assessment.level !in emitOnLevels) return Decision.BelowThreshold(assessment)

        val hash = sha1(window)
        if (lastEmittedHashByPackage[packageName] == hash) return Decision.Deduped

        lastEmittedHashByPackage[packageName] = hash
        lastEmitAtMs[packageName] = nowMs
        return Decision.Emit(
            packageName = packageName,
            assessment = assessment,
            text = window,
            contextHash = hash,
        )
    }

    @Synchronized
    fun reset(packageName: String? = null) {
        if (packageName == null) {
            lastAssessmentAtMs.clear()
            lastEmitAtMs.clear()
            lastEmittedHashByPackage.clear()
            buffer.clearAll()
        } else {
            lastAssessmentAtMs.remove(packageName)
            lastEmitAtMs.remove(packageName)
            lastEmittedHashByPackage.remove(packageName)
            buffer.clear(packageName)
        }
    }

    private fun sha1(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }
    }

    companion object {
        const val MIN_INTERVAL_MS = 600L
        const val COOLDOWN_MS = 60_000L
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
