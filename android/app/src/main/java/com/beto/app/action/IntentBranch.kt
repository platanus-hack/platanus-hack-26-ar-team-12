package com.beto.app.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.beto.app.util.LogTags
import timber.log.Timber
import java.net.URLEncoder

sealed class ActionResult {
    object Launched : ActionResult()
    data class Failed(val reason: String) : ActionResult()
}

data class WhatsappIntentSpec(
    val action: String,
    val uri: String,
    val packageName: String,
)

object IntentBranch {
    const val WHATSAPP_PACKAGE = "com.whatsapp"

    fun buildWhatsappIntentSpec(contact: DemoContact, message: String): WhatsappIntentSpec {
        val digitsOnlyPhone = contact.e164.filter(Char::isDigit)
        return WhatsappIntentSpec(
            action = Intent.ACTION_VIEW,
            uri = "https://wa.me/$digitsOnlyPhone?text=${encodeForWaMe(message)}",
            packageName = WHATSAPP_PACKAGE,
        )
    }

    fun sendWhatsapp(context: Context, contact: DemoContact, message: String): ActionResult {
        val spec = buildWhatsappIntentSpec(contact, message)
        val uri = Uri.parse("https://wa.me/${contact.e164.filter(Char::isDigit)}?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.whatsapp")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            Timber.tag(LogTags.INTENT).i(
                "Launching WhatsApp package=%s messageLength=%d",
                spec.packageName,
                message.length,
            )
            context.startActivity(intent)
            ActionResult.Launched
        } catch (e: RuntimeException) {
            Timber.tag(LogTags.INTENT).w(e, "WhatsApp launch failed reason=%s", e::class.simpleName)
            ActionResult.Failed(e::class.simpleName ?: "runtime_exception")
        }
    }

    private fun encodeForWaMe(message: String): String =
        URLEncoder.encode(message, Charsets.UTF_8.name()).replace("+", "%20")
}
