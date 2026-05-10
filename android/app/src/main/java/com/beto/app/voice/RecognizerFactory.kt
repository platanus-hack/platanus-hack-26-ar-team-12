package com.beto.app.voice

import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer
import com.beto.app.util.LogTags
import timber.log.Timber

object RecognizerFactory {
    fun create(context: Context): SpeechRecognizer {
        val canOnDevice = canUseOnDevice(context)
        return if (canOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
                Timber.tag(LogTags.STT).d("Using on-device recognizer")
            }
        } else {
            SpeechRecognizer.createSpeechRecognizer(context).also {
                Timber.tag(LogTags.STT).d("Using cloud-backed recognizer")
            }
        }
    }

    fun shouldPreferOffline(context: Context): Boolean = canUseOnDevice(context)

    private fun canUseOnDevice(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
}
