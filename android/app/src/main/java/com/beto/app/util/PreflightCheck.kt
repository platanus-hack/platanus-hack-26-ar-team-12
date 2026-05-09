package com.beto.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.beto.app.service.BetoAccessibilityService
import com.beto.app.voice.TtsManager
import timber.log.Timber

data class PreflightResult(
    val overlayOk: Boolean,
    val accessibilityOk: Boolean,
    val ttsOk: Boolean,
    val microphoneOk: Boolean,
) {
    val allOk: Boolean
        get() = overlayOk && accessibilityOk && ttsOk && microphoneOk

    val missing: List<String>
        get() = buildList {
            if (!overlayOk) add("overlay")
            if (!accessibilityOk) add("accessibility")
            if (!ttsOk) add("tts")
            if (!microphoneOk) add("microphone")
        }
}

object PreflightCheck {

    fun check(context: Context): PreflightResult {
        val result = PreflightResult(
            overlayOk = Settings.canDrawOverlays(context),
            accessibilityOk = isAccessibilityServiceEnabled(context),
            ttsOk = TtsManager.isReady,
            microphoneOk = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
        Timber.tag(LogTags.TTS).i(
            "Preflight result: overlay=%s a11y=%s tts=%s mic=%s missing=%s",
            result.overlayOk,
            result.accessibilityOk,
            result.ttsOk,
            result.microphoneOk,
            result.missing,
        )
        return result
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${BetoAccessibilityService::class.java.name}"
        val secureSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()

        if (secureSetting.split(':').any { it.equals(expected, ignoreCase = true) }) {
            return true
        }

        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
        return accessibilityManager.getEnabledAccessibilityServiceList(0).orEmpty().any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == BetoAccessibilityService::class.java.name
        }
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
