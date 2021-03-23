package com.antrov.timesrapse

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.antrov.timesrapse.modules.AlarmHelper
import com.antrov.timesrapse.modules.StorageManager
import com.antrov.timesrapse.service.ForegroundService
import com.antrov.timesrapse.utils.xlog.Promtail
import com.antrov.timesrapse.utils.xlog.PromtailPrinter
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject


class MainActivity : AppCompatActivity() {

    private val cameraPermission = 1001
    private val storagePermission = 2002

    private val logger = XLog.tag("MainActivity").build()

    private val alarm: AlarmHelper by inject()
    private val storge: StorageManager by inject()

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.action?.let {
                when (ForegroundService.BroadcastAction.valueOf(it)) {
                    ForegroundService.BroadcastAction.Captured -> {
                        storge.catalogStats()
                    }
                    ForegroundService.BroadcastAction.Failed -> {
                        Toast.makeText(getApplicationContext(), "failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupServiceSwitch()
        setupPromtailSwitch()

        checkCameraPermission()
    }

    override fun onResume() {
        IntentFilter()
            .apply {
                ForegroundService.BroadcastAction.values().forEach {
                    addAction(it.toString())
                }
            }
            .let {
                registerReceiver(receiver, it)
            }

        super.onResume()
    }

    override fun onPause() {
        unregisterReceiver(receiver)
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            cameraPermission -> checkStoragePermission()
            storagePermission -> startCapture()
        }
    }

    private fun checkCameraPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermission)
        } else {
            checkStoragePermission()
        }
    }

    private fun checkStoragePermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                storagePermission
            )
        } else {
            startCapture()
        }
    }

    private fun startCapture() {
        alarm.startAlarm()
        ForegroundService.request(this, ForegroundService.Command.Capture)
    }

    private fun setupServiceSwitch() {
        findViewById<Switch>(R.id.service_switch).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                when (isChecked) {
                    true -> alarm.startAlarm()
                    false -> alarm.cancelAlarm()
                }
            }
        }
    }

    private fun setupPromtailSwitch() {
        findViewById<Switch>(R.id.promtail_switch).apply {
            val prefs = getPreferences(MODE_PRIVATE)
            val promtail: Promtail = get()

            val promKey = "promtail.enabled"

            setOnCheckedChangeListener { _, isChecked ->
                promtail.isEnabled = isChecked
                prefs.edit().apply { putBoolean(promKey, isChecked) }.apply()
            }

            getPreferences(MODE_PRIVATE).getBoolean(promKey, true).let {
                isChecked = it
                promtail.isEnabled = it
            }
        }
    }
}

