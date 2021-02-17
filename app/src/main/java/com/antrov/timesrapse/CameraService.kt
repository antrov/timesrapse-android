package com.antrov.timesrapse

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.*
import android.os.Process.THREAD_PRIORITY_BACKGROUND
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Process
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.timerTask


class CameraService : Service() {
    private val channelId = "ForegroundServiceChannel"
    private val timer = Timer()

    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            Log.d(TAG, "handleMessage")
            readyCamera()
        }
    }

    /**
     * CAMERA HANDLER
     */

    protected val CAMERA_CALIBRATION_DELAY: Int = 500
    protected val TAG = "myLog"
    protected val CAMERACHOICE = CameraCharacteristics.LENS_FACING_BACK
    protected var cameraCaptureStartTime: Long = 0
    protected var cameraDevice: CameraDevice? = null
    protected var session: CameraCaptureSession? = null
    protected var imageReader: ImageReader? = null

    /* callback and listeners */

    protected var cameraStateCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.d(TAG, "CameraDevice.StateCallback onOpened")
                cameraDevice = camera
                actOnReadyCameraDevice()
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "CameraDevice.StateCallback onDisconnected")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "CameraDevice.StateCallback onError $error")
            }
        }

    protected var sessionStateCallback: CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
            override fun onReady(session: CameraCaptureSession) {
                this@CameraService.session = session
                try {
                    session.capture(createCaptureRequest()!!, null, null)
                    cameraCaptureStartTime = System.currentTimeMillis()
                } catch (e: CameraAccessException) {
                    Log.e(TAG, e.message!!)
                }
            }

            override fun onConfigured(session: CameraCaptureSession) {}
            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }

    protected var onImageAvailableListener =
        OnImageAvailableListener { reader ->
            Log.d(TAG, "onImageAvailable")
            val img: Image? = reader.acquireLatestImage()
            if (img != null) {
                if (System.currentTimeMillis() > cameraCaptureStartTime + CAMERA_CALIBRATION_DELAY) {
                    processImage(img)
                }
                img.close()
            } else {
                Log.d(TAG, "image is nil")
            }
        }

    /* methods */

    fun readyCamera() {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val pickedCamera = getCamera(manager)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "permission not granted")
                return
            }
            manager.openCamera(pickedCamera!!, cameraStateCallback, null)

            imageReader = ImageReader.newInstance(
                1920,
                1088,
                ImageFormat.JPEG,
                2 /* images buffered */
            )
            imageReader!!.setOnImageAvailableListener(onImageAvailableListener, null)
            Log.d(TAG, "imageReader created")
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.message!!)
        }
    }

    fun getCamera(manager: CameraManager): String? {
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics =
                    manager.getCameraCharacteristics(cameraId!!)
                val cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING)!!
                if (cOrientation == CAMERACHOICE) {
                    return cameraId
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        Log.d(TAG, "unabled to get camera")
        return null
    }

    private fun processImage(image: Image) {
        if (session != null) {
            try {
                session!!.abortCaptures()
            } catch (e: CameraAccessException) {
                e.message?.let { Log.e(TAG, it) }
            }
            session!!.close()
            session = null
            cameraDevice!!.close()
        }
        //Process image data
        val buffer: ByteBuffer
        val bytes: ByteArray
        var success = false
        val file =
            File(
                Environment.getExternalStorageDirectory()
                    .toString() + "/Pictures/" + UUID.randomUUID().toString() + ".jpg"
            )
        var output: FileOutputStream? = null
        if (image.format == ImageFormat.JPEG) {
            buffer = image.planes[0].buffer
            bytes =
                ByteArray(buffer.remaining()) // makes byte array large enough to hold image
            buffer.get(bytes) // copies image from buffer to byte array
            try {
                output = FileOutputStream(file)
                output.write(bytes) // write the byte array to file
                success = true
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                image.close() // close this to free up buffer for other images
                if (null != output) {
                    try {
                        output.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
//        session!!.close()
    }

    protected fun createCaptureRequest(): CaptureRequest? {
        return try {
            val builder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(imageReader!!.surface)
            builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            builder.build()
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.message!!)
            null
        }
    }

    fun actOnReadyCameraDevice() {
        try {
            cameraDevice!!.createCaptureSession(
                Arrays.asList(imageReader!!.surface),
                sessionStateCallback,
                null
            )
        } catch (e: CameraAccessException) {
            e.message?.let { Log.e(TAG, it) }
        }
    }

    /**
     * SERVICE HELPER
     */

    companion object {
        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, CameraService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }

        fun stopService(context: Context) {
            val stopIntent = Intent(context, CameraService::class.java)
            context.stopService(stopIntent)
        }
    }

    /**
     * SERVICE LIFECYCLE
     */

    override fun onCreate() {
        HandlerThread(
            "ServiceStartArguments",
            android.os.Process.THREAD_PRIORITY_BACKGROUND
        ).apply {
            start()

            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show()

        //do heavy work on a background thread
        val input = intent.getStringExtra("inputExtra")
        val serviceChannel = NotificationChannel(
            channelId, "Foreground Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java)!!.createNotificationChannel(serviceChannel)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Foreground Service Kotlin Example")
            .setContentText(input)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
        Log.println(Log.DEBUG, "LOG", "starting service")

//        serviceHandler?.obtainMessage()?.also { msg ->
//            msg.arg1 = startId
//            serviceHandler?.sendMessage(msg)
//        }

        timer.scheduleAtFixedRate(timerTask {
            serviceHandler?.obtainMessage()?.also { msg ->
                msg.arg1 = startId
                serviceHandler?.sendMessage(msg)
            }
        }, 100, 10000)

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        // We don't provide binding, so return null
        return null
    }

    override fun onDestroy() {
        Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show()
        try {
            session!!.abortCaptures()
        } catch (e: CameraAccessException) {
            e.message?.let { Log.e(TAG, it) }
        }
        session!!.close()
    }
}