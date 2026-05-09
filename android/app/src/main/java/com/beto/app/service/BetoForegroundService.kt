package com.beto.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.beto.app.BetoApplication
import com.beto.app.MainActivity
import com.beto.app.R
import com.beto.app.action.PlanCController
import com.beto.app.bus.AgentBus
import com.beto.app.bus.AgentCommand
import com.beto.app.bus.AgentEvent
import com.beto.app.overlay.OverlayManager
import com.beto.app.util.LogTags
import com.beto.app.voice.TtsManager
import com.beto.app.voice.VoiceCaptureActivity
import kotlinx.coroutines.launch
import timber.log.Timber

class BetoForegroundService : LifecycleService() {

    private var bootGreetingPlayed = false
    private lateinit var planCController: PlanCController

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(LogTags.TTS).i("BetoForegroundService.onCreate")
        BetoApplication.ensureNotificationChannel(this)
        startForegroundCorrectly()
        planCController = PlanCController(
            context = this,
            scope = lifecycleScope,
            sendCommand = AgentBus::command,
        )

        lifecycleScope.launch {
            AgentBus.commands.collect { command ->
                when (command) {
                    is AgentCommand.Speak -> {
                        Timber.tag(LogTags.TTS).d("Cmd Speak -> %s", command.text)
                        TtsManager.speak(command.text)
                    }
                    is AgentCommand.StartVoiceCapture -> {
                        startActivity(VoiceCaptureActivity.startIntent(this@BetoForegroundService, command.startedAtMs))
                    }
                }
            }
        }

        lifecycleScope.launch {
            AgentBus.events.collect { event ->
                when (event) {
                    is AgentEvent.BubbleTapped -> {
                        Timber.tag(LogTags.STT).i("Bubble tapped -> voice capture")
                        planCController.startVoiceCapture(event.startedAtMs)
                    }
                    is AgentEvent.VoiceCaptured -> {
                        planCController.onVoiceCaptured(event.text, event.elapsedMs)
                    }
                    is AgentEvent.VoiceCaptureFailed -> {
                        planCController.onVoiceCaptureFailed(event.reason, event.elapsedMs)
                    }
                    else -> Unit
                }
            }
        }

        lifecycleScope.launch { AgentBus.emit(AgentEvent.ServiceStarted) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Timber.tag(LogTags.TTS).i("onStartCommand startId=%d", startId)

        OverlayManager.show(this)

        if (!bootGreetingPlayed) {
            bootGreetingPlayed = true
            Timber.tag(LogTags.TTS).i("Disparando speakBootGreeting (D-10)")
            TtsManager.speakBootGreeting()
            lifecycleScope.launch { AgentBus.emit(AgentEvent.BootCompleted) }
        }

        return START_STICKY
    }

    private fun startForegroundCorrectly() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Timber.tag(LogTags.TTS).i("startForeground OK type=microphone")
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, flags)

        return NotificationCompat.Builder(this, BetoApplication.FGS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_beto_notification)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(R.string.fgs_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        Timber.tag(LogTags.TTS).i("BetoForegroundService.onDestroy")
        OverlayManager.hide(this)
        lifecycleScope.launch { AgentBus.emit(AgentEvent.ServiceStopped) }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context): Intent =
            Intent(context, BetoForegroundService::class.java)
    }
}
