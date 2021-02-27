package com.antrov.timesrapse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    private val tag = "timesrapse/alarmReceiver"

    private val channelId = "App Alert Notification ID"
    private val channelName = "App Alert Notification"

    private val notificationSucceededId = 161
    private val notificationFailedId = 181

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "onReceive")
        HeadlessCapture().takePhoto(context) { success ->
            PrefsCounter().incrementCounter(context, success).also { counter ->
                counter.first.takeIf { it > 0 && success }?.let {
                    notify(context, notificationSucceededId, "Photos succeeded $it")
                }

                counter.second.takeIf { it > 0 && !success }?.let {
                    notify(context, notificationFailedId, "Photos failed $it")
                }

                Promtail(context).log(success, counter.first, counter.second)
            }
        }
    }

    private fun notify(context: Context, notificationId: Int, value: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_NONE
        )
        channel.enableVibration(false)

        manager.createNotificationChannel(channel)

        val intent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent, 0)

        NotificationCompat.Builder(context, channelId)
            .setContentTitle("Timesrapse")
            .setContentText(value)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0L))
            .build().also {
                manager.notify(notificationId, it)
            }
    }
}