package com.beto.app.scam

/** Detector individual: input texto ya normalizado → 0..1 hit con evidencia. */
interface SignalDetector {
    val signal: Signal
    fun detect(normalizedText: String): SignalHit?
}

/** Detector basado en una lista OR de regex; el primero que matchea gana. */
internal class RegexSignalDetector(
    override val signal: Signal,
    private val patterns: List<Regex>,
) : SignalDetector {

    override fun detect(normalizedText: String): SignalHit? {
        for (regex in patterns) {
            val match = regex.find(normalizedText) ?: continue
            return SignalHit(signal = signal, evidence = match.value)
        }
        return null
    }
}
