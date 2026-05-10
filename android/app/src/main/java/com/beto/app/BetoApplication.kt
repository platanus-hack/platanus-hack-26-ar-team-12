package com.beto.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.beto.app.memory.UserMemoryStore
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import timber.log.Timber

/**
 * Application class — boot-time wiring.
 *
 * Responsabilidades:
 *  1. Plant Timber DebugTree para que los tags Beto-XXX funcionen desde T0.
 *  2. Pre-warmear TtsManager (Pitfall #3 — init temprano evita race del primer speak).
 *  3. Crear el NotificationChannel `beto_service` antes de que BetoForegroundService.startForeground lo use.
 */
class BetoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Timber primero — todo lo demás puede loguear desde acá en adelante
        Timber.plant(Timber.DebugTree())
        Timber.tag(LogTags.TTS).i("BetoApplication.onCreate — Beto starting up")

        // TTS pre-warmed (Pitfall #3) — init temprano, NO en el momento del primer comando
        TtsManager.init(this)

        // Notif channel del FGS (D-15) — declarado acá para que el canal exista antes
        // de que BetoForegroundService.startForeground lo use.
        ensureNotificationChannel(this)

        userMemoryStore = UserMemoryStore(this)
    }

    companion object {
        const val FGS_CHANNEL_ID = "beto_service"
        lateinit var userMemoryStore: UserMemoryStore
            private set

        fun ensureNotificationChannel(ctx: Context) {
            val nm = ctx.getSystemService<NotificationManager>() ?: return
            if (nm.getNotificationChannel(FGS_CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                FGS_CHANNEL_ID,
                ctx.getString(R.string.fgs_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = ctx.getString(R.string.fgs_notification_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
