package com.antrov.timesrapse

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.hardware.camera2.*
import android.os.*
import android.provider.ContactsContract.Intents.Insert.ACTION
import android.provider.SyncStateContract
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timerTask


class ForegroundService : Service() {

    private val tag = "cameraService"
    private val serviceId = 1
    private val channelId = "ForegroundServiceChannel"

    enum class Command {
        Start, Stop, Capture
    }

    companion object {
        fun request(context: Context, command: Command) {
            val startIntent = Intent(context, ForegroundService::class.java).apply {
                action = command.name
            }
            ContextCompat.startForegroundService(context, startIntent)
        }
    }

    override fun onCreate() {
    }

    private fun createNotification(
        counter: Pair<Int, Int>? = null,
        success: Boolean = true
    ): Notification {
        val serviceChannel = NotificationChannel(
            channelId, "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        getSystemService(NotificationManager::class.java)?.apply {
            createNotificationChannel(serviceChannel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )

        val text = counter?.let {
            arrayOf(
                "Photos succeeded: ${it.first}",
                "Photos failed: ${it.second}"
            ).joinToString("\n")
        } ?: "Service started"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Capture running")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0L))
            .build()
    }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (Command.valueOf(intent.action ?: "")) {
            Command.Start -> {
                Log.d(tag, "starting service")
                when (Build.VERSION.SDK_INT) {
                    in 30..Int.MAX_VALUE -> @RequiresApi(Build.VERSION_CODES.Q) {
                        startForeground(
                            serviceId,
                            createNotification(),
                            FOREGROUND_SERVICE_TYPE_CAMERA
                        )
                    }
                    else -> startForeground(
                        serviceId,
                        createNotification()
                    )
                }
            }
            Command.Stop -> {
                stopForeground(true)
                stopSelf()
            }
            Command.Capture ->
                HeadlessCapture().takePhoto(this) { success ->
                    PrefsCounter().incrementCounter(this, success).also { counter ->
                        with(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager) {
                            notify(serviceId, createNotification(counter, success))
                        }
                        Promtail(this).log(success, counter.first, counter.second)
                    }
                }
        }


        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        Log.d(tag, "onDestroy")
    }
}
