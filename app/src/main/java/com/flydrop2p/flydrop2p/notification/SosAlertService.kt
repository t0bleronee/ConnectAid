package com.flydrop2p.flydrop2p.notification

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.flydrop2p.flydrop2p.R

class SosAlertService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 10_000L) ?: 10_000L
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "SOS Alert"
        val body = intent?.getStringExtra(EXTRA_BODY) ?: "Emergency alert"
        val notifId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, 9999) ?: 9999

        startForeground(notifId, buildNotification(title, body))
        startAlarm(durationMs)

        handler.postDelayed({ stopSelf() }, durationMs)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(title: String, body: String): Notification {
        val builder = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)

        return builder.build()
    }

    private fun startAlarm(durationMs: Long) {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@SosAlertService, alarmUri)
                isLooping = true
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnErrorListener { _, _, _ ->
                    stopAlarm()
                    true
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
            // If media fails, continue with vibration-only
        }

        try {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200), 0)
                vibrator.vibrate(effect)
                handler.postDelayed({ vibrator.cancel() }, durationMs)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 200), 0)
                handler.postDelayed({ vibrator.cancel() }, durationMs)
            }
        } catch (_: Exception) { }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    companion object {
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_NOTIFICATION_ID = "extra_notif_id"
    }
}


