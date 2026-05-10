package com.beto.app.companion

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * Acciones rápidas — cada chip dispara una app nativa para que el usuario use SU buscador
 * (de contactos, mensajes, alarmas), sin pasar por el LLM ni un picker custom.
 *
 * Estética: chip outline-only, fondo transparente, borde sutil, ícono unicode "vacío".
 */
internal data class QuickAction(
    val label: String,
    val icon: String,
    val launch: (Context) -> Unit,
)

internal object QuickActionDefs {
    val actions: List<QuickAction> = listOf(
        QuickAction(
            label = "Llamar",
            icon = "☎",
            launch = ::launchDialer,
        ),
        QuickAction(
            label = "WhatsApp",
            icon = "✎",
            launch = ::launchWhatsApp,
        ),
        QuickAction(
            label = "Mensaje",
            icon = "✉",
            launch = ::launchSms,
        ),
        QuickAction(
            label = "Alarma",
            icon = "⏰",
            launch = ::launchAlarmClock,
        ),
    )

    private fun launchDialer(context: Context) {
        // ACTION_DIAL abre la app de teléfono con su propio buscador de contactos.
        tryStart(context, Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "dialer")
    }

    private fun launchWhatsApp(context: Context) {
        // Estrategia 1: launchIntent del paquete (requiere queries en manifest para Android 11+).
        val pm = context.packageManager
        val candidatePackages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in candidatePackages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                tryStart(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "whatsapp")
                return
            }
        }
        // Estrategia 2: deeplink wa.me (abre WhatsApp si está instalado, va al chat de inicio).
        val deeplink = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (deeplink.resolveActivity(pm) != null) {
            tryStart(context, deeplink, "whatsapp_deeplink")
            return
        }
        // Estrategia 3: si nada funciona, Play Store. Loguea para diagnostic.
        Timber.tag(LogTags.ACTION).w("WhatsApp launch — todos los caminos fallaron, abriendo Play Store")
        tryStart(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "whatsapp_install",
        )
    }

    private fun launchSms(context: Context) {
        // ACTION_VIEW con sms: scheme abre la app de SMS por defecto en su pantalla principal.
        tryStart(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("sms:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "sms",
        )
    }

    private fun launchAlarmClock(context: Context) {
        // Estrategia 1: ACTION_SHOW_ALARMS (estándar AOSP).
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (showAlarms.resolveActivity(context.packageManager) != null) {
            tryStart(context, showAlarms, "clock_alarms")
            return
        }
        // Estrategia 2: launchIntent de la clock app del fabricante.
        val pm = context.packageManager
        val candidates = listOf(
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.sec.android.app.clockpackage",     // Samsung
            "com.oneplus.deskclock",                  // OnePlus
            "com.miui.deskclock",                     // Xiaomi
        )
        for (pkg in candidates) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                tryStart(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "clock_$pkg")
                return
            }
        }
        Timber.tag(LogTags.ACTION).w("Alarma — no se encontró ninguna app de reloj instalada")
    }

    private fun tryStart(context: Context, intent: Intent, label: String) {
        try {
            context.startActivity(intent)
            Timber.tag(LogTags.ACTION).i("QUICK_ACTION_LAUNCHED label=%s", label)
        } catch (e: ActivityNotFoundException) {
            Timber.tag(LogTags.ACTION).w(e, "No app for QUICK_ACTION label=%s", label)
        } catch (e: SecurityException) {
            Timber.tag(LogTags.ACTION).w(e, "Permission denied QUICK_ACTION label=%s", label)
        }
    }
}

/**
 * Columna de chips apilados verticalmente, alineados a la izquierda. Pequeños, outline-only.
 */
@Composable
internal fun QuickActionsColumn(
    onActionTap: (QuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        QuickActionDefs.actions.forEach { action ->
            QuickActionChip(action = action, onClick = { onActionTap(action) })
        }
    }
}

@Composable
private fun QuickActionChip(action: QuickAction, onClick: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = action.icon,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = action.label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
