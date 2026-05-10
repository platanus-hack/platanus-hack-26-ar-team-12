package com.beto.app.trust

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.beto.app.util.LogTags
import timber.log.Timber

/**
 * Helpers para disparar la llamada al contacto de confianza desde el overlay del Escudo
 * Antiestafas. La regla en este flujo es: una sola acción decisiva, sin steps extra.
 *
 * Estrategia:
 *  - Si tenemos `CALL_PHONE` granted → `ACTION_CALL` (marca directo, sin que el user
 *    tenga que apretar "llamar" en el dialer otra vez — adultos mayores se traban en ese
 *    paso intermedio).
 *  - Si no → `ACTION_DIAL` (abre el dialer con el número precargado, requiere un tap más
 *    pero NUNCA falla por permission). Es nuestra red de seguridad.
 *
 * Ambos caminos vuelven `true` si el intent se disparó con éxito; el caller decide si
 * mostrar feedback warm en caso `false` (improbable: el dialer es app de sistema).
 */
object TrustedCallIntents {

    fun call(context: Context, contact: TrustedContact): Boolean {
        val tel = telUri(contact.phoneNumberRaw)
        if (tel == null) {
            Timber.tag(LogTags.INTENT).w("TRUSTED_CALL phone vacío, abort")
            return false
        }

        val canDirectCall = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED

        val intent = if (canDirectCall) {
            Intent(Intent.ACTION_CALL, tel)
        } else {
            Intent(Intent.ACTION_DIAL, tel)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Timber.tag(LogTags.INTENT).i(
                "TRUSTED_CALL %s rel=%s direct=%s",
                tel.toString().take(24),
                contact.relationship.name,
                canDirectCall,
            )
            true
        } catch (t: Throwable) {
            Timber.tag(LogTags.INTENT).w(t, "TRUSTED_CALL failed, fallback to DIAL")
            // Último intento: forzar DIAL (sin ACTION_CALL) — el dialer del sistema casi nunca falla.
            try {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, tel).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            } catch (t2: Throwable) {
                Timber.tag(LogTags.INTENT).e(t2, "TRUSTED_CALL fallback también falló")
                false
            }
        }
    }

    private fun telUri(rawPhone: String): Uri? {
        val cleaned = rawPhone.filter { it.isDigit() || it == '+' }
        if (cleaned.isEmpty()) return null
        return Uri.parse("tel:$cleaned")
    }
}
