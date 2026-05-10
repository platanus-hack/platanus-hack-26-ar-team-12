package com.beto.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
import com.beto.app.contacts.ContactRepository
import com.beto.app.service.BetoForegroundService
import com.beto.app.util.LogTags
import com.beto.app.util.PreflightCheck
import com.beto.app.voice.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(LogTags.TTS).i("MainActivity.onCreate")
    }

    override fun onResume() {
        super.onResume()
        scope.launch {
            if (!TtsManager.isReady) {
                delay(TTS_GRACE_MS)
            }
            maybePromptVoiceInstall()
            handlePreflightResult(PreflightCheck.check(this@MainActivity))
        }
    }

    /**
     * Si el device no tiene voz masculina disponible, abrimos UNA SOLA VEZ el flow del
     * engine para instalar voces (ACTION_INSTALL_TTS_DATA). Persistimos el flag para no
     * molestar en cada arranque.
     */
    private fun maybePromptVoiceInstall() {
        if (TtsManager.selectedVoiceIsLikelyMale) return
        val prefs = getSharedPreferences("beto_tts", MODE_PRIVATE)
        if (prefs.getBoolean("voice_install_prompted", false)) return
        prefs.edit().putBoolean("voice_install_prompted", true).apply()

        val intent = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
            .onFailure {
                // Fallback: abrí los settings de voz para que el user instale a mano.
                runCatching {
                    startActivity(
                        Intent("com.android.settings.TTS_SETTINGS")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                Timber.tag(LogTags.TTS).w(it, "ACTION_INSTALL_TTS_DATA failed — fell back to settings")
            }
    }

    private fun handlePreflightResult(result: com.beto.app.util.PreflightResult) {
        if (result.allOk) {
            handleContactsPermissionOrStart()
            return
        }

        scope.launch { AgentBus.emit(AgentEvent.PermissionsMissing(result.missing)) }

        when {
            !result.overlayOk -> {
                TtsManager.speak(getString(R.string.tts_overlay_missing))
                PreflightCheck.openOverlaySettings(this)
            }
            !result.accessibilityOk -> {
                TtsManager.speak(getString(R.string.tts_accessibility_missing))
                PreflightCheck.openAccessibilitySettings(this)
            }
            !result.microphoneOk -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    MIC_PERMISSION_REQUEST,
                )
            }
            !result.ttsOk -> {
                TtsManager.speak(getString(R.string.tts_tts_missing))
            }
        }
    }

    private fun handleContactsPermissionOrStart() {
        val contacts = ContactRepository(applicationContext)
        if (contacts.hasPermission() || isContactsLimitedMode()) {
            startBetoAndFinish()
            return
        }

        showContactsPermissionOnboarding()
    }

    private fun showContactsPermissionOnboarding() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val explanation = TextView(this).apply {
            text = getString(R.string.contacts_permission_explanation)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        val grant = Button(this).apply {
            text = getString(R.string.contacts_permission_button_grant)
            textSize = 20f
            setOnClickListener {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    CONTACTS_PERMISSION_REQUEST,
                )
            }
        }
        val skip = Button(this).apply {
            text = getString(R.string.contacts_permission_button_skip)
            textSize = 20f
            setOnClickListener {
                setContactsLimitedMode(true)
                TtsManager.speak(getString(R.string.contacts_permission_denied_voice))
                startBetoAndFinish()
            }
        }
        container.addView(
            explanation,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(
            grant,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.addView(
            skip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        setContentView(container)
    }

    private fun startBetoAndFinish() {
        Timber.tag(LogTags.TTS).i("Preflight OK — starting BetoForegroundService and finishing")
        val intent = BetoForegroundService.startIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MIC_PERMISSION_REQUEST -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    handlePreflightResult(PreflightCheck.check(this))
                }
            }
            CONTACTS_PERMISSION_REQUEST -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    setContactsLimitedMode(false)
                    TtsManager.speak(getString(R.string.contacts_permission_granted_voice))
                } else {
                    setContactsLimitedMode(true)
                    TtsManager.speak(getString(R.string.contacts_permission_denied_voice))
                }
                startBetoAndFinish()
            }
        }
    }

    private fun isContactsLimitedMode(): Boolean =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_CONTACTS_LIMITED_MODE, false)

    private fun setContactsLimitedMode(enabled: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONTACTS_LIMITED_MODE, enabled)
            .apply()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TTS_GRACE_MS = 2_000L
        private const val MIC_PERMISSION_REQUEST = 100
        private const val CONTACTS_PERMISSION_REQUEST = 101
        private const val PREFS_NAME = "beto_permissions"
        private const val KEY_CONTACTS_LIMITED_MODE = "contacts_limited_mode"
    }
}
