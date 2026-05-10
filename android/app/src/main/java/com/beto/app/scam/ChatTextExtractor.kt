package com.beto.app.scam

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Extrae texto chat-relevante desde un AccessibilityEvent (o, fallback, desde el
 * AccessibilityNodeInfo root del activeWindow).
 *
 * Estrategia (intencionalmente simple para hackathon):
 * 1) Si el evento trae `text` directo (típico de typeViewTextChanged y typeNotificationStateChanged),
 *    lo usamos — es la última burbuja que cambió.
 * 2) Si no, BFS sobre el `source` del evento (la View que disparó), tomando solo nodos
 *    "leaf-like" con texto, ignorando inputs (EditText) y nodos clickeables grandes
 *    (botones del chat: enviar, mic, cámara).
 * 3) Cap fuerte de nodos visitados para no clavarse en árboles patológicos.
 *
 * NO intenta segmentar quién envía qué burbuja: el ScamRiskEngine opera sobre el blob
 * concatenado y la calibración del threshold (3+ signals) absorbe el ruido de mezcla.
 */
class ChatTextExtractor(
    private val maxNodes: Int = DEFAULT_MAX_NODES,
    private val maxCharsPerExtraction: Int = DEFAULT_MAX_CHARS,
) {

    /**
     * Extrae el mejor candidato de texto del evento. Devuelve null si el evento
     * no aporta nada útil (ej. clicks sin texto, navegación de UI).
     */
    fun extract(event: AccessibilityEvent): String? {
        val direct = pickDirectText(event)
        if (!direct.isNullOrBlank()) return direct.take(maxCharsPerExtraction)

        val source = event.source ?: return null
        val text = bfsCollectText(source)
        return text?.take(maxCharsPerExtraction)
    }

    /**
     * Para fallback: extrae texto sobre un node arbitrario (ej. rootInActiveWindow).
     * Útil cuando llegamos por typeWindowContentChanged sin source preciso.
     */
    fun extractFromTree(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        return bfsCollectText(root)?.take(maxCharsPerExtraction)
    }

    private fun pickDirectText(event: AccessibilityEvent): String? {
        val parts = event.text ?: return null
        if (parts.isEmpty()) return null
        return parts.asSequence()
            .filterNotNull()
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = " ")
            .ifBlank { null }
    }

    private fun bfsCollectText(root: AccessibilityNodeInfo): String? {
        val collected = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++

            if (isContentNode(node)) {
                val nodeText = node.text?.toString()?.trim().orEmpty()
                if (nodeText.isNotEmpty()) {
                    if (collected.isNotEmpty()) collected.append(' ')
                    collected.append(nodeText)
                    if (collected.length >= maxCharsPerExtraction) break
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
        }

        return collected.toString().ifBlank { null }
    }

    /**
     * Filtramos para quedarnos con burbujas de texto y descartar UI chrome:
     * - EditText (campo de input — el user escribiendo no es estafa)
     * - Botones (icon buttons del chat)
     * - Nodos sin texto
     */
    private fun isContentNode(node: AccessibilityNodeInfo): Boolean {
        val text = node.text ?: return false
        if (text.isBlank()) return false
        val cls = node.className?.toString().orEmpty()
        if (cls.contains("EditText", ignoreCase = true)) return false
        if (cls.contains("Button", ignoreCase = true)) return false
        return true
    }

    companion object {
        const val DEFAULT_MAX_NODES = 250
        const val DEFAULT_MAX_CHARS = 600
    }
}
