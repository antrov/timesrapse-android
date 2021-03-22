package com.antrov.timesrapse.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.antrov.timesrapse.*
import com.antrov.timesrapse.modules.StorageManager
import com.elvishew.xlog.XLog
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinApiExtension
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*

class ForegroundService : Service(), HeadlessCaptureCallback {

    private val logger = XLog.tag("ForegroundService").build()

    private val channelId = "1730428d-df4e-4a9f-b5b7-5d395bcbe8d0"
    private val channelName = "ForegroundCaptureChannel"

    private val serviceId = 2

    private val storage: StorageManager by inject()
    private val capture = HeadlessCapture(this, WeakReference(this))

    enum class Command {
        Start, Stop, Capture
    }

    enum class BroadcastAction {
        Captured, Failed
    }

    companion object {
        fun request(context: Context, command: Command) {
            Intent(context, ForegroundService::class.java)
                .apply { action = command.name }
                .let { ContextCompat.startForegroundService(context, it) }
        }
    }

    override fun onCreate() {
    }

    private fun createNotification(counter: Pair<Int, Int>? = null): Notification {
        NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            .apply { enableVibration(false) }
            .let { channel ->
                getSystemService(NotificationManager::class.java)?.apply {
                    createNotificationChannel(channel)
                }
            }

        val intent = Intent(this, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TASK }
            .let { PendingIntent.getActivity(this, 0, it, 0) }

        val text = counter?.let {
            arrayOf(
                "Photos succeeded: ${it.first}",
                "Photos failed: ${it.second}"
            ).joinToString("\n")
        } ?: "Service started"

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Capture running")
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(intent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0L))
            .build()
    }

    @KoinApiExtension
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val action = intent.action ?: run {
            logger.e("cannot start with empty action")
            return START_STICKY
        }

        val cmd = Command.valueOf(action)
        logger.d("received `$cmd` start command")

        when (cmd) {
            Command.Start -> {
                logger.d("starting service")
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
            Command.Capture -> {
                capture.takePhoto()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        logger.i("onDestroy")
    }

    override fun onCaptured(data: ByteBuffer) {
        storage.store(data)

        Intent()
            .apply { action = BroadcastAction.Captured.toString() }
            .let { sendBroadcast(it) }
    }

    override fun onFailed(error: Throwable) {

    }
}
