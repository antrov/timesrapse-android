package com.antrov.timesrapse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*

class HeadlessCapture() {

    private val delay: Int = 2000
    private val tag = "cameraService/headlessCapture"

    private var cameraCaptureStartTime: Long = 0
    private var cameraRequest: CaptureRequest? = null
    private var imageReader: ImageReader? = null
    private var processed = false
    private var captureCallback: ((success: Boolean) -> Unit)? = null

    private var cameraStateCallback = object : CameraDevice.StateCallback() {
        private val tag = "cameraService/cameraStateCallback"

        override fun onOpened(camera: CameraDevice) {
            Log.d(tag, "onOpened")

            val surface = imageReader?.surface ?: run {
                captureCallback?.invoke(false)
                Log.e(tag, "imageReader surface is empty")
                return
            }

            try {
                camera.apply {
                    createCaptureSession(listOf(surface), sessionStateCallback, null)

                    cameraRequest =
                        createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(surface)
                            set(
                                CaptureRequest.CONTROL_MODE,
                                CameraMetadata.CONTROL_MODE_AUTO
                            )
                        }.build()
                }
            } catch (e: CameraAccessException) {
                captureCallback?.invoke(false)
                Log.e(tag, "camera session and request creation failed", e)
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(tag, "CameraDevice.StateCallback onDisconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(tag, "CameraDevice.StateCallback onError $error")
        }
    }

    private var sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        private val tag = "cameraService/sessionStateCallback"

        override fun onReady(session: CameraCaptureSession) {
            if (processed) {
                session.close()
                Log.d(tag, "already processed. Breaking")
                return
            }

            val request = cameraRequest ?: run {
                Log.e(tag, "cameraRequest is empty")
                return
            }

            try {
                session.capture(request, null, null)
                Log.d(tag, "capture requested")
            } catch (e: CameraAccessException) {
                captureCallback?.invoke(false)
                Log.e(tag, "session capture request", e)
            }
        }

        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(tag, "onConfigured")
            cameraCaptureStartTime = System.currentTimeMillis()
        }

        override fun onClosed(session: CameraCaptureSession) {
            Log.d(tag, "onClosed")
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {}
    }

    private var onImageAvailableListener = OnImageAvailableListener { reader ->
        val latestImage = reader.acquireLatestImage()

        val img = latestImage.takeIf { it.format == ImageFormat.JPEG } ?: run {
            latestImage?.close()
            captureCallback?.invoke(false)
            Log.e(tag, "last acquired frame is empty or has non JPEG")
            return@OnImageAvailableListener
        }

        if (System.currentTimeMillis() < cameraCaptureStartTime + delay || processed) {
            img.close()
            Log.w(tag, "it is not valid time or processed = $processed")
            return@OnImageAvailableListener
        }

        processed = true

        val name = (System.currentTimeMillis() / 1000L).toString() + ".jpg"
        val path = Environment.getExternalStorageDirectory().toString() + "/Pictures/"
        val file = File(path + name)

        var output: FileOutputStream? = null
        val bytes: ByteArray

        img.planes[0].buffer.apply {
            bytes = ByteArray(remaining())
            get(bytes)
        }

        Log.d(tag, "writing to file $path$name")

        try {
            output = FileOutputStream(file)
            output.write(bytes)
            Log.d(tag, "image written at path $path$name")
        } catch (e: FileNotFoundException) {
            captureCallback?.invoke(false)
            Log.e(tag, "file $path$name not found", e)
        } catch (e: IOException) {
            captureCallback?.invoke(false)
            Log.e(tag, "i/o", e)
        } finally {
            img.close()
            output?.apply {
                try {
                    close()
                    captureCallback?.invoke(true)
                } catch (e: Exception) {
                    Log.e(tag, "output close", e)
                    captureCallback?.invoke(false)
                }
            }
        }
    }

/* methods */

    fun takePhoto(context: Context, callback: ((success: Boolean) -> Unit)?) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = manager.cameraIdList.firstOrNull {
                manager
                    .getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: run {
                Log.e(tag, "not found back camera id")
                return
            }

            if (
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(tag, "no permission to use camera")
                return
            }

            manager.openCamera(cameraId, cameraStateCallback, null)

            processed = false
            captureCallback = callback

            imageReader = ImageReader.newInstance(4032, 3024, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener(onImageAvailableListener, null)
            }

            Log.d(tag, "imageReader created")
        } catch (e: CameraAccessException) {
            callback?.invoke(false)
            Log.e(tag, "failed to open cameera", e)
        }
    }

}
