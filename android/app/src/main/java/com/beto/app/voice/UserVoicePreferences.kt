package com.beto.app.voice

import android.content.Context

/**
 * Persistencia simple del override manual del usuario sobre qué voz TTS usa Beto.
 * Si está seteado, `TtsManager.applyUserPreferenceOrAuto` busca esa voz por nombre y la
 * aplica. Si no, se cae al `VoiceSelector` automático.
 */
object UserVoicePreferences {

    private const val PREFS_NAME = "beto_tts"
    private const val KEY_VOICE_NAME = "user_chosen_voice_name"

    fun savedVoiceName(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VOICE_NAME, null)
            ?.takeIf { it.isNotBlank() }

    fun save(context: Context, voiceName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VOICE_NAME, voiceName)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_VOICE_NAME)
            .apply()
    }
}
