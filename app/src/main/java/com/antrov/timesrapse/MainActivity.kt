package com.antrov.timesrapse

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val cameraPermission = 1001
    private val storagePermission = 2002
    private val tag = "timesrapse/main"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkCameraPermission()
        setupCounterButton()
        setupPromtailControls()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            cameraPermission -> checkStoragePermission()
            storagePermission -> setupServiceSwitch()
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
            setupServiceSwitch()
        }
    }

    private fun setupServiceSwitch() {
        val serviceSwitch = findViewById<Switch>(R.id.service_switch)
        val alarm = AlarmHelper(this)

        val onEnabled = {
            ForegroundService.request(this, ForegroundService.Command.Start)
            alarm.startAlarm()
        }

        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(tag, "onSwitch")
            when (isChecked) {
                true -> onEnabled.invoke()
                false -> {
                    ForegroundService.request(this, ForegroundService.Command.Stop)
                    alarm.cancelAlarm()
                }
            }
        }

        onEnabled.invoke()
    }

    private fun setupCounterButton() {
        findViewById<Button>(R.id.counterReset).let {
            it.setOnClickListener {
                PrefsCounter().resetCounter(this)
            }
        }
    }

    private fun setupPromtailControls() {
        val prefs = getSharedPreferences(Promtail.sharedPrefName, Context.MODE_PRIVATE)

        findViewById<Button>(R.id.promtailCredsButton).setOnClickListener {
            val user = findViewById<EditText>(R.id.promtailUser).text.toString()
            val key = findViewById<EditText>(R.id.promtailKey).text.toString()

            with(prefs.edit()) {
                putString(Promtail.sharedPrefUser, user)
                putString(Promtail.sharedPrefKey, key)
                apply()
            }
        }

        findViewById<Switch>(R.id.promtailSwitch).setOnCheckedChangeListener { _, checked ->
            with(prefs.edit()) {
                putBoolean(Promtail.sharedPrefPromtailEnabled, checked)
                apply()
            }
        }

        findViewById<EditText>(R.id.promtailUser).setText(
            prefs.getString(
                Promtail.sharedPrefUser,
                ""
            )
        )
        findViewById<EditText>(R.id.promtailKey).setText(
            prefs.getString(
                Promtail.sharedPrefKey,
                ""
            )
        )
        findViewById<Switch>(R.id.promtailSwitch).isChecked =
            prefs.getBoolean(Promtail.sharedPrefPromtailEnabled, false)
    }
}

