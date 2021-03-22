package com.antrov.timesrapse.modules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import com.antrov.timesrapse.service.ForegroundService
import com.elvishew.xlog.XLog

class AlarmReceiver : BroadcastReceiver() {

    private val logger = XLog.tag("AlarmReceiver").build()

    override fun onReceive(context: Context, intent: Intent) {
        logger.d("onReceive")
        ForegroundService.request(context, ForegroundService.Command.Capture)
    }
}

class AlarmHelper(private val context: Context) {

    private val requestCode = 1410
    private val logger = XLog.tag("AlarmHelper").build()

    fun startAlarm() {
        val interval = 60 * 1000L

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, 0)

        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime(),
            interval,
            pendingIntent
        )

        logger.d("alarm started")
        Toast.makeText(context, "Starting alarm", Toast.LENGTH_LONG).show()
    }

    fun cancelAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, 0)
        alarmManager.cancel(pendingIntent)

        logger.d("alarm cancelled")
        Toast.makeText(context, "Stopping alarm", Toast.LENGTH_LONG).show()
    }
}