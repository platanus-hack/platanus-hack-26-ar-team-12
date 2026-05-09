package com.beto.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentEvent
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
            handlePreflightResult(PreflightCheck.check(this@MainActivity))
        }
    }

    private fun handlePreflightResult(result: com.beto.app.util.PreflightResult) {
        if (result.allOk) {
            Timber.tag(LogTags.TTS).i("Preflight OK — starting BetoForegroundService and finishing")
            val intent = BetoForegroundService.startIntent(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            finish()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == MIC_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            handlePreflightResult(PreflightCheck.check(this))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TTS_GRACE_MS = 2_000L
        private const val MIC_PERMISSION_REQUEST = 100
    }
}
