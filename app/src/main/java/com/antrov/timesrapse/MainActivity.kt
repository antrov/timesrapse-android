package com.antrov.timesrapse

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val cameraPermission = 1001
    private val storagePermission = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkCameraPermission()
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
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), storagePermission)
        } else {
            setupServiceSwitch()
        }
    }

    private fun setupServiceSwitch() {
        val serviceSwitch = findViewById<Switch>(R.id.service_switch)
        val interval = 10000L

        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            when (isChecked) {
                true -> CameraService.startService(this, interval)
                false -> CameraService.stopService(this)
            }
        }

        CameraService.startService(this, interval)
    }
}