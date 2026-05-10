package com.beto.app.onboarding

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

/**
 * Modelo declarativo de cada permiso que Beto necesita. La pantalla de onboarding consume
 * esto para renderizar la lista con su estado actual y un botón "Activar" que abre la
 * pantalla de Settings correspondiente (o pide el permiso runtime).
 */
data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val critical: Boolean,
    val isGranted: (Context) -> Boolean,
    val activate: (Context) -> ActivationResult,
)

sealed class ActivationResult {
    /** El item necesita un Intent al usuario (Settings) — la actividad lo lanza. */
    data class OpenSettings(val intent: Intent) : ActivationResult()
    /** El item se resuelve con `requestPermissions` runtime — la actividad lo solicita. */
    data class RequestRuntime(val permissions: Array<String>) : ActivationResult()
    /** Ya está concedido (idempotente). */
    object AlreadyGranted : ActivationResult()
}

/**
 * Lista de los permisos críticos y opcionales de Beto, en el orden que el usuario los va
 * a otorgar. El primero que falte determina qué se muestra primero en la lista.
 */
object BetoPermissions {

    val items: List<PermissionItem> = listOf(
        // CRÍTICOS — Beto no funciona sin estos
        PermissionItem(
            id = "overlay",
            title = "Mostrar sobre otras apps",
            description = "Para que la burbuja de Beto aparezca arriba de cualquier app.",
            critical = true,
            isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
            activate = { ctx ->
                ActivationResult.OpenSettings(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ),
        PermissionItem(
            id = "accessibility",
            title = "Servicio de accesibilidad",
            description = "Para que Beto pueda leer la pantalla y guiarte con flechas visuales.",
            critical = true,
            isGranted = { ctx -> isAccessibilityEnabled(ctx) },
            activate = {
                ActivationResult.OpenSettings(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ),
        PermissionItem(
            id = "microphone",
            title = "Micrófono",
            description = "Para que Beto escuche tus comandos por voz.",
            critical = true,
            isGranted = { ctx ->
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            },
            activate = {
                ActivationResult.RequestRuntime(arrayOf(Manifest.permission.RECORD_AUDIO))
            },
        ),
        // OPCIONALES — Beto funciona sin estos pero pierde features
        PermissionItem(
            id = "contacts",
            title = "Contactos",
            description = "Para que Beto pueda llamar o mandar mensajes a tus contactos reales (no solo demo).",
            critical = false,
            isGranted = { ctx ->
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
                    PackageManager.PERMISSION_GRANTED
            },
            activate = {
                ActivationResult.RequestRuntime(arrayOf(Manifest.permission.READ_CONTACTS))
            },
        ),
        PermissionItem(
            id = "phone",
            title = "Teléfono",
            description = "Para hacer llamadas directas con el comando \"llamá a...\".",
            critical = false,
            isGranted = { ctx ->
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
                    PackageManager.PERMISSION_GRANTED
            },
            activate = {
                ActivationResult.RequestRuntime(arrayOf(Manifest.permission.CALL_PHONE))
            },
        ),
        PermissionItem(
            id = "battery",
            title = "Batería sin restricciones",
            description = "Para que Beto no se apague cuando dejás el celular un rato.",
            critical = false,
            isGranted = { ctx ->
                val pm = ctx.getSystemService(android.os.PowerManager::class.java)
                pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            },
            activate = { ctx ->
                ActivationResult.OpenSettings(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ),
        PermissionItem(
            id = "tts",
            title = "Voz instalada",
            description = "Beto necesita una voz en español. Si no anda, instalá la voz desde tu sistema.",
            critical = false,
            isGranted = { TtsManager.isReady },
            activate = {
                // Abrí el flow de instalación de voces TTS (engine specific)
                ActivationResult.OpenSettings(
                    Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ),
    )

    fun allCriticalGranted(context: Context): Boolean =
        items.filter { it.critical }.all { it.isGranted(context) }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${BetoAccessibilityService::class.java.name}"
        val secureSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        if (secureSetting.split(':').any { it.equals(expected, ignoreCase = true) }) return true
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        return manager.getEnabledAccessibilityServiceList(0).orEmpty().any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == BetoAccessibilityService::class.java.name
        }
    }
}
