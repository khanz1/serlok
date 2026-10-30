package com.khanz1.serlok.ui.screens.room

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.khanz1.serlok.MainActivity
import com.khanz1.serlok.R
import com.khanz1.serlok.service.RoomAlert

object RoomAlertNotificationHelper {
    const val CHANNEL_ID = "serlok_room_alert"
    const val EXTRA_FOCUS_USER_ID = "focusUserId"
    private const val NOTIFICATION_ID = 3001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null && existing.lockscreenVisibility != Notification.VISIBILITY_PUBLIC) {
            manager.deleteNotificationChannel(CHANNEL_ID)
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alert_channel_description)
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun showAlert(context: Context, alert: RoomAlert) {
        val name = alert.name.ifBlank { context.getString(R.string.member_fallback) }
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FOCUS_USER_ID, alert.userId)
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alert.incidentId.hashCode(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sourceText = context.getString(R.string.alert_shake_body, alert.magnitudeG)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.alert_title, name))
            .setContentText(sourceText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(sourceText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(showPendingIntent)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.alert_action_open), showPendingIntent)

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID + alert.incidentId.hashCode(), builder.build())
    }
}
