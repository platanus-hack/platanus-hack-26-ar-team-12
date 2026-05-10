package com.beto.app.scam

/**
 * Acumulador de texto por package. AccessibilityService emite eventos parciales
 * (cada burbuja del chat es un evento), por eso el engine necesita ver una ventana
 * concatenada de los últimos N chars antes de evaluar. Sin esto, frases partidas
 * en dos burbujas ("cambié de número" + "transferime 80 mil") no cruzan signals.
 *
 * Diseño:
 * - Pure Kotlin, sin Android. Inyectable a ScamWatcher.
 * - LRU por package — adultos mayores típicamente miran 1–2 chats a la vez.
 * - Cap en chars, no líneas — los detectors operan sobre el blob completo.
 * - Dedupe trivial: si el último append es igual al tail, no agrega.
 */
class ScamMessageBuffer(
    private val maxCharsPerPackage: Int = DEFAULT_MAX_CHARS,
    private val maxPackages: Int = DEFAULT_MAX_PACKAGES,
) {

    private val buffers = LinkedHashMap<String, StringBuilder>(maxPackages, 0.75f, true)

    @Synchronized
    fun append(packageName: String, text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return snapshot(packageName)

        val sb = buffers.getOrPut(packageName) { StringBuilder(maxCharsPerPackage) }

        if (endsWith(sb, clean)) return sb.toString()

        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(clean)

        if (sb.length > maxCharsPerPackage) {
            val drop = sb.length - maxCharsPerPackage
            sb.delete(0, drop)
        }

        evictIfOverCapacity()
        return sb.toString()
    }

    @Synchronized
    fun snapshot(packageName: String): String =
        buffers[packageName]?.toString().orEmpty()

    @Synchronized
    fun clear(packageName: String) {
        buffers.remove(packageName)
    }

    @Synchronized
    fun clearAll() {
        buffers.clear()
    }

    @Synchronized
    fun size(packageName: String): Int = buffers[packageName]?.length ?: 0

    private fun endsWith(sb: StringBuilder, fragment: String): Boolean {
        if (fragment.length > sb.length) return false
        val start = sb.length - fragment.length
        for (i in fragment.indices) {
            if (sb[start + i] != fragment[i]) return false
        }
        return true
    }

    private fun evictIfOverCapacity() {
        while (buffers.size > maxPackages) {
            val it = buffers.entries.iterator()
            if (!it.hasNext()) break
            it.next()
            it.remove()
        }
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 800
        const val DEFAULT_MAX_PACKAGES = 4
    }
}
