package com.beto.app.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.beto.app.llm.ToolDescriptors
import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * Ejecutor de acciones basadas en Intents de Android.
 * (D-09) Si un Intent directo puede realizar la tarea, lo usamos.
 */
class IntentActionExecutor(
    private val context: Context,
    private val contactsResolver: ContactsResolver
) {

    /**
     * Ejecuta una acción basada en el nombre de la herramienta y sus parámetros.
     * Retorna true si pudo disparar el intent, false si hubo error o no se encontró el contacto.
     */
    fun execute(toolName: String, args: Map<String, String>): Boolean {
        Timber.tag(LogTags.ACTION).i("Ejecutando tool: %s con args: %s", toolName, args)

        return when (toolName) {
            ToolDescriptors.MAKE_CALL -> makeCall(args["contact"])
            ToolDescriptors.SEND_WHATSAPP -> sendWhatsApp(args["contact"], args["message"])
            ToolDescriptors.OPEN_MAPS -> openMaps(args["query"])
            ToolDescriptors.SEND_SMS -> sendSms(args["contact"], args["message"])
            else -> {
                Timber.tag(LogTags.ACTION).w("Tool no soportada por IntentActionExecutor: %s", toolName)
                false
            }
        }
    }

    private fun makeCall(contactQuery: String?): Boolean {
        val contact = contactQuery?.let { contactsResolver.resolveContact(it) } ?: return false
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${contact.phoneNumber}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryLaunch(intent)
    }

    private fun sendWhatsApp(contactQuery: String?, message: String?): Boolean {
        val contact = contactQuery?.let { contactsResolver.resolveContact(it) } ?: return false
        val msg = message ?: ""
        
        // Intent para WhatsApp: ACTION_SENDTO con el número en la URI es más directo
        // O ACTION_VIEW con api.whatsapp.com/send
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=${contact.phoneNumber}&text=${Uri.encode(msg)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryLaunch(intent)
    }

    private fun openMaps(query: String?): Boolean {
        if (query.isNullOrBlank()) return false
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryLaunch(mapIntent)
    }

    private fun sendSms(contactQuery: String?, message: String?): Boolean {
        val contact = contactQuery?.let { contactsResolver.resolveContact(it) } ?: return false
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${contact.phoneNumber}")
            putExtra("sms_body", message ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryLaunch(intent)
    }

    private fun tryLaunch(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.tag(LogTags.ACTION).e(e, "Error al lanzar intent")
            false
        }
    }
}
