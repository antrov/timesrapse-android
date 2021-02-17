package com.antrov.timesrapse

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.camera2.*
import android.os.*
import android.provider.ContactsContract.Intents.Insert.ACTION
import android.provider.SyncStateContract
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timerTask


class CameraService : Service() {

    private val tag = "cameraService"

    private val channelId = "ForegroundServiceChannel"
    private val timer = Timer()

    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper, private var capture: HeadlessCapture) :
        Handler(looper) {

        private val tag = "cameraService/handler"

        override fun handleMessage(msg: Message) {
            Log.d(tag, "received trigger message")
            capture.takePhoto()
        }
    }

    companion object {
        fun startService(context: Context, interval: Long) {
            val startIntent = Intent(context, CameraService::class.java).apply {
                action = "start"
                putExtra("interval", interval)
            }
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, CameraService::class.java).apply {
                action = "stop"
            }
            ContextCompat.startForegroundService(context, stopIntent)
        }
    }

    override fun onCreate() {
        val capture =
            HeadlessCapture(this, getSystemService(Context.CAMERA_SERVICE) as CameraManager)

        HandlerThread(
            "ServiceStartArguments",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()

            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper, capture)
        }
    }

    private fun createNotification(interval: Long): Notification {
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

        val intervalFormatted = SimpleDateFormat("mm:ss", Locale.ENGLISH)
            .format(Date(interval))

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Timesrapse capture")
            .setContentText("Capture interval: $intervalFormatted ms")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == "stop") {
            Log.d(tag, "stopping service")
            timer.cancel()
            stopForeground(true)
            stopSelf()
            return START_STICKY
        }

        val interval = intent.getLongExtra("interval", 300000)
        val notification = createNotification(interval)

        startForeground(1, notification)
        Log.d(tag, "starting service")

        timer.scheduleAtFixedRate(timerTask {
            serviceHandler?.obtainMessage()?.also {
                serviceHandler?.sendMessage(it)
            }
        }, 100, interval)

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