package com.beto.app.scam

/**
 * Whitelist de packages que el ScamWatcher monitorea proactivamente. Mantenerla chica:
 * Accessibility events son ruidosos y queremos cero CPU en apps no-mensajería.
 *
 * Ojo: esta whitelist SOLO afecta el watcher proactivo. La whitelist NO se setea en
 * accessibility_service_config.xml porque el Modo Guía (Phase 4) necesita operar
 * sobre cualquier app que el LLM elija.
 */
object ScamPackages {

    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val GOOGLE_MESSAGES = "com.google.android.apps.messaging"
    const val SAMSUNG_MESSAGES = "com.samsung.android.messaging"
    const val ANDROID_MMS = "com.android.mms"
    const val TELEGRAM = "org.telegram.messenger"

    val DEFAULT_WHITELIST: Set<String> = setOf(
        WHATSAPP,
        WHATSAPP_BUSINESS,
        GOOGLE_MESSAGES,
        SAMSUNG_MESSAGES,
        ANDROID_MMS,
        TELEGRAM,
    )

    fun isWatched(packageName: CharSequence?, whitelist: Set<String> = DEFAULT_WHITELIST): Boolean {
        if (packageName.isNullOrEmpty()) return false
        return packageName.toString() in whitelist
    }
}
