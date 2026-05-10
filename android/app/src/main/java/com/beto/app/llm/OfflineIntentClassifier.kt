package com.beto.app.llm

import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * Clasificador offline basado en Regex para intenciones obvias.
 * (Pitfall #6) Minimizar llamadas al LLM si el comando es directo.
 */
class OfflineIntentClassifier {

    data class ClassifiedIntent(
        val toolName: String,
        val args: Map<String, String>
    )

    /**
     * Intenta clasificar el texto usando patrones comunes en es-AR.
     * Retorna ClassifiedIntent si hubo match, null si requiere LLM.
     */
    fun classify(text: String): ClassifiedIntent? {
        var input = text.trim().lowercase()
        
        // Strip wake word if present (D-08)
        if (input.startsWith("beto ")) {
            input = input.removePrefix("beto ").trim()
        }
        
        Timber.tag(LogTags.LLM).d("Clasificando offline: %s", input)

        // 1. LLAMADAS: "llamá a [contacto]", "llamame a [contacto]"
        val callRegex = Regex("""^llam[aá](?:me)? a\s+(.+)$""")
        callRegex.find(input)?.let { match ->
            val contact = match.groupValues[1]
            return ClassifiedIntent(ToolDescriptors.MAKE_CALL, mapOf("contact" to contact))
        }

        // 2. WHATSAPP: "mandále un whatsapp a [contacto] que [mensaje]", "mensaje por whatsapp a [contacto] [mensaje]"
        val wsRegex = Regex("""^(?:mand[aá](?:le)?|envi[aá](?:le)?|escrib[ií](?:le)?)\s*(?:un\s+)?whatsapp\s+a\s+([^\s]+)(?:\s+(?:que|diciendo)\s+)?\s*(.*)$""")
        wsRegex.find(input)?.let { match ->
            val contact = match.groupValues[1]
            val message = match.groupValues[2].trim()
            return ClassifiedIntent(ToolDescriptors.SEND_WHATSAPP, mapOf("contact" to contact, "message" to message))
        }

        // 3. SMS / MENSAJE: "mandále un mensaje a [contacto] que [mensaje]"
        val msgRegex = Regex("""^(?:mand[aá](?:le)?|envi[aá](?:le)?|escrib[ií](?:le)?|dec[ií](?:le)?)\s*(?:un\s+)?mensaje\s+a\s+([^\s]+)(?:\s+(?:que|diciendo)\s+)?\s*(.*)$""")
        msgRegex.find(input)?.let { match ->
            val contact = match.groupValues[1]
            val message = match.groupValues[2].trim()
            return ClassifiedIntent(ToolDescriptors.SEND_SMS, mapOf("contact" to contact, "message" to message))
        }

        // 4. MAPAS: "abrí el mapa hasta [lugar]", "cómo llego a [lugar]"
        val mapsRegex = Regex("""^(?:abr[ií] el mapa hasta|c[oó]mo llego a)\s+(.+)$""")
        mapsRegex.find(input)?.let { match ->
            val query = match.groupValues[1]
            return ClassifiedIntent(ToolDescriptors.OPEN_MAPS, mapOf("query" to query))
        }

        // 5. APPS: "abrí [app]"
        val openAppRegex = Regex("""^abr[ií]\s+(.+)$""")
        openAppRegex.find(input)?.let { match ->
            val app = match.groupValues[1]
            if (app.contains("whatsapp")) {
                return ClassifiedIntent(ToolDescriptors.SEND_WHATSAPP, mapOf("contact" to "", "message" to ""))
            }
            // Otras apps podrían requerir el loop agéntico o un mapeo local
        }

        Timber.tag(LogTags.LLM).d("No hubo match offline para: %s", input)
        return null
    }
}
