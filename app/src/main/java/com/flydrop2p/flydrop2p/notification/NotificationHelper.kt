package com.flydrop2p.flydrop2p.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.flydrop2p.flydrop2p.MainActivity
import com.flydrop2p.flydrop2p.R

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val KEY_CHAT_ACCOUNT_ID = "chat_account_id"
        const val CHANNEL_CALLS = "calls"
        const val CHANNEL_SOS = "sos"
        const val KEY_GROUP_NAME = "group_name"
        const val KEY_CALL_ACCOUNT_ID = "call_account_id"
        const val KEY_CALL_ACTION = "call_action"
        const val ACTION_ACCEPT = "accept"
        const val ACTION_DECLINE = "decline"
    }

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)

            val callChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            )
            callChannel.description = "Incoming call notifications"
            callChannel.setBypassDnd(true)
            manager.createNotificationChannel(callChannel)

            val sosChannel = NotificationChannel(
                CHANNEL_SOS,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            sosChannel.description = "Emergency SOS alerts"
            sosChannel.enableVibration(true)
            sosChannel.vibrationPattern = longArrayOf(0, 5000)
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            sosChannel.setSound(alarmUri, audioAttrs)
            manager.createNotificationChannel(sosChannel)
        }
    }

    fun showMessageNotification(accountId: Long, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(KEY_CHAT_ACCOUNT_ID, accountId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            accountId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("chat_" + accountId)

        manager.notify(accountId.toInt(), builder.build())
    }

    fun showGroupMessageNotification(group: String, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(KEY_GROUP_NAME, group)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            (group.hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("group_$group")

        manager.notify((group.hashCode()), builder.build())
    }

    fun showIncomingCallNotification(accountId: Long, title: String) {
        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(KEY_CALL_ACCOUNT_ID, accountId)
            putExtra(KEY_CALL_ACTION, ACTION_ACCEPT)
        }
        val declineIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(KEY_CALL_ACCOUNT_ID, accountId)
            putExtra(KEY_CALL_ACTION, ACTION_DECLINE)
        }

        val acceptPending = PendingIntent.getActivity(
            context,
            (accountId * 10 + 1).toInt(),
            acceptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val declinePending = PendingIntent.getActivity(
            context,
            (accountId * 10 + 2).toInt(),
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .addAction(0, "Decline", declinePending)
            .addAction(0, "Accept", acceptPending)
            .setAutoCancel(false)

        manager.notify((accountId * 100).toInt(), builder.build())
    }

    fun cancelIncomingCallNotification(accountId: Long) {
        manager.cancel((accountId * 100).toInt())
    }

    fun showSosNotification(senderAccountId: Long, title: String, body: String, durationMs: Long, latitude: Double? = null, longitude: Double? = null) {
        // Start foreground service to play alarm sound and vibrate continuously
        val serviceIntent = Intent(context, SosAlertService::class.java).apply {
            putExtra(SosAlertService.EXTRA_DURATION_MS, durationMs)
            putExtra(SosAlertService.EXTRA_TITLE, title)
            putExtra(SosAlertService.EXTRA_BODY, body)
            putExtra(SosAlertService.EXTRA_NOTIFICATION_ID, (senderAccountId * 1000).toInt())
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (_: Exception) {}

        val tapIntent = Intent(context, SosCopyReceiver::class.java).apply {
            if (latitude != null && longitude != null) {
                putExtra("lat", latitude)
                putExtra("lon", longitude)
            }
        }

        val contentPending = PendingIntent.getBroadcast(
            context,
            (senderAccountId * 1000).toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SOS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(true)
            .setTimeoutAfter(durationMs)
            .setContentIntent(contentPending)


        val id = (senderAccountId * 1000).toInt()
        manager.notify(id, builder.build())
    }
}


