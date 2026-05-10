package com.beto.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.onboarding.ActivationResult
import com.beto.app.onboarding.BetoPermissions
import com.beto.app.onboarding.OnboardingScreen
import com.beto.app.onboarding.PermissionItem
import com.beto.app.onboarding.VoiceSettingsActivity
import com.beto.app.service.BetoForegroundService
import com.beto.app.trust.TrustedContactActivity
import com.beto.app.ui.BetoTheme
import com.beto.app.util.LogTags
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Onboarding activity. Single Compose screen que lista TODOS los permisos que Beto necesita
 * con su estado actual y un botón "Activar" por item. Cuando todos los críticos están OK,
 * el botón "Continuar" se habilita y arranca el `BetoForegroundService`.
 *
 * Se re-checkean los permisos en `onResume` (al volver de Settings) para que los toggles
 * "Activar" se conviertan en "Listo" automáticamente sin reload manual.
 */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Compose state observable. Cada onResume + cada respuesta de permiso refresca.
    private val permissionStatus = mutableStateMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(LogTags.TTS).i("MainActivity.onCreate")
        refreshStatuses()
        setContent {
            BetoTheme(darkTheme = false) {
                val trustedContact by BetoApplication.trustedContactsRepository.state.collectAsState()
                val subtitle = trustedContact?.let { "${it.relationship.label}: ${it.displayName}" }
                    ?: "Aún sin configurar"
                OnboardingScreen(
                    statuses = permissionStatus,
                    onActivate = { item -> handleActivate(item) },
                    onContinue = { startBetoAndFinish() },
                    onOpenVoiceSettings = {
                        startActivity(
                            Intent(this, VoiceSettingsActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    onOpenTrustedContact = {
                        startActivity(
                            Intent(this, TrustedContactActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    trustedContactSubtitle = subtitle,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El user puede volver de Settings con permisos otorgados — refrescá la lista.
        refreshStatuses()
    }

    private fun refreshStatuses() {
        BetoPermissions.items.forEach { item ->
            permissionStatus[item.id] = item.isGranted(this)
        }
        // Notificá al bus si hay permisos críticos faltantes (compat con listeners legacy).
        val missing = BetoPermissions.items
            .filter { it.critical && !it.isGranted(this) }
            .map { it.id }
        if (missing.isNotEmpty()) {
            scope.launch { AgentBus.emit(AgentEvent.PermissionsMissing(missing)) }
        }
    }

    private fun handleActivate(item: PermissionItem) {
        when (val result = item.activate(this)) {
            is ActivationResult.OpenSettings -> {
                runCatching { startActivity(result.intent) }
                    .onFailure {
                        Timber.tag(LogTags.TTS).w(it, "No pude abrir Settings para %s", item.id)
                    }
            }
            is ActivationResult.RequestRuntime -> {
                ActivityCompat.requestPermissions(this, result.permissions, REQUEST_CODE_RUNTIME)
            }
            ActivationResult.AlreadyGranted -> {
                permissionStatus[item.id] = true
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_RUNTIME) {
            refreshStatuses()
        }
    }

    private fun startBetoAndFinish() {
        Timber.tag(LogTags.TTS).i("Onboarding completed -> starting BetoForegroundService")
        val intent = BetoForegroundService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        finish()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_RUNTIME = 200
    }
}
