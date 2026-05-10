package com.beto.app.action

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.beto.app.memory.ContactRef
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
        return sendWhatsapp(context, contact.e164, message)
    }

    fun sendWhatsapp(context: Context, contact: ContactRef, message: String): ActionResult {
        return sendWhatsapp(context, contact.phoneE164, message)
    }

    private fun sendWhatsapp(context: Context, phoneE164: String, message: String): ActionResult {
        val digitsOnlyPhone = phoneE164.filter(Char::isDigit)
        val uri = Uri.parse("https://wa.me/$digitsOnlyPhone?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .setPackage("com.whatsapp")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            Timber.tag(LogTags.INTENT).i("INTENT_LAUNCHED tool=send_whatsapp package=%s", WHATSAPP_PACKAGE)
            context.startActivity(intent)
            ActionResult.Launched
        } catch (e: RuntimeException) {
            Timber.tag(LogTags.INTENT).w(e, "WhatsApp launch failed reason=%s", e::class.simpleName)
            ActionResult.Failed(e::class.simpleName ?: "runtime_exception")
        }
    }

    fun makeCall(context: Context, phoneE164: String): ActionResult {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (context is Activity) {
                ActivityCompat.requestPermissions(
                    context,
                    arrayOf(Manifest.permission.CALL_PHONE),
                    CALL_PERMISSION_REQUEST,
                )
            }
            return ActionResult.Failed("missing_call_permission")
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneE164"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStart(context, intent, "make_call")
    }

    fun sendSms(context: Context, phoneE164: String, message: String): ActionResult {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneE164")).apply {
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryStart(context, intent, "send_sms")
    }

    fun openMaps(context: Context, query: String): ActionResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStart(context, intent, "open_maps")
    }

    private fun tryStart(context: Context, intent: Intent, tool: String): ActionResult =
        try {
            context.startActivity(intent)
            Timber.tag(LogTags.INTENT).i("INTENT_LAUNCHED tool=%s package=%s", tool, intent.`package`)
            ActionResult.Launched
        } catch (e: RuntimeException) {
            Timber.tag(LogTags.INTENT).w(e, "Intent launch failed tool=%s reason=%s", tool, e::class.simpleName)
            ActionResult.Failed(e::class.simpleName ?: "runtime_exception")
        }

    private fun encodeForWaMe(message: String): String =
        URLEncoder.encode(message, Charsets.UTF_8.name()).replace("+", "%20")

    private const val CALL_PERMISSION_REQUEST = 301
}
