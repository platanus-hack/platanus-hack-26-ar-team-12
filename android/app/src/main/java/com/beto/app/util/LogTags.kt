package com.beto.app.util

/**
 * Tags estándar para Timber. Todos los components DEBEN usar estos exactamente.
 * Filtrar en logcat: `adb logcat -s "Beto-Accessibility:D" "Beto-LLM:D" ...`
 */
object LogTags {
    const val ACCESSIBILITY = "Beto-Accessibility"
    const val LLM = "Beto-LLM"
    const val ACTION = "Beto-Action"
    const val STT = "Beto-STT"
    const val INTENT = "Beto-Intent"
    const val TTS = "Beto-TTS"
    const val BUS = "Beto-Bus"
    const val MEMORY = "Beto-Memory"
}
