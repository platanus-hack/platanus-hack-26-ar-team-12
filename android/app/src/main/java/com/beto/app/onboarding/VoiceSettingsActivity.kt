package com.beto.app.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.beto.app.ui.BetoTheme

/**
 * Pantalla de configuración de la voz TTS de Beto.
 * Lista todas las voces es disponibles (con su locale y "calidad" detectada como masculina/femenina/neural)
 * y permite probar y elegir una. La elección persiste en SharedPreferences y se aplica al boot del FGS.
 */
class VoiceSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BetoTheme(darkTheme = false) {
                VoiceSettingsScreen(
                    onClose = { finish() },
                )
            }
        }
    }
}
