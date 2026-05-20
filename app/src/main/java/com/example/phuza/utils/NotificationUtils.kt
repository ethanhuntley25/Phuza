package com.example.phuza.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.phuza.R

object NotificationUtils {

    const val CHANNEL_ID: String = "phuza-general"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "General",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "App notifications"
                    setShowBadge(true)
                }
                try {
                    mgr.createNotificationChannel(ch)
                } catch (e: Exception) {
                    Log.e("NotifUtils", "createNotificationChannel failed", e)
                }
            }
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }


    fun show(
        context: Context,
        title: String,
        text: String,
        id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
        @DrawableRes smallIconRes: Int = R.drawable.ic_bell,
        pendingIntent: PendingIntent? = null,
        isAutoCancel: Boolean = true
    ) {
        if (!canPostNotifications(context)) {
            Log.w("NotifUtils", "Permission not granted; skipping notify")
            return
        }
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(isAutoCancel)

        pendingIntent?.let { builder.setContentIntent(it) }

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (se: SecurityException) {
            Log.e("NotifUtils", "notify() SecurityException", se)
        } catch (e: Exception) {
            Log.e("NotifUtils", "notify() failed", e)
        }
    }

}
