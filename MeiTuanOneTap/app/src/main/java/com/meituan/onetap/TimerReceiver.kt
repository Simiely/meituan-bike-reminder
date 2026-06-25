package com.meituan.onetap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * AlarmManager 到点时触发，弹出锁车提醒通知。
 */
class TimerReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "meituan_bike_alarm"
        const val NOTIFICATION_ID = 3001
        const val ACTION_TIMES_UP = "com.meituan.onetap.TIMES_UP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TIMES_UP) {
            showNotification(context)
        }
    }

    private fun showNotification(context: Context) {
        createChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🚲 该锁车了！")
            .setContentText("50分钟到了，请记得锁车！")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "骑行提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "50分钟倒计时到点提醒锁车"
                enableVibration(true)
                enableLights(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
