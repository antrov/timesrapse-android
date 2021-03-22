package com.antrov.timesrapse.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import androidx.core.app.ActivityCompat
import com.elvishew.xlog.XLog
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

interface HeadlessCaptureCallback {
    fun onCaptured(data: ByteBuffer) {}
    fun onFailed(error: Throwable) {}
}

class HeadlessCapture(private val context: Context, private val captureCallback: WeakReference<HeadlessCaptureCallback>) {

    enum class CaptureState {
        CLOSED, CAMERA_OPENED, SESSION_OPENED, CAPTURED
    }

    private val delay: Int = 5000

    private var cameraCaptureStartTime: Long = 0
    private var cameraRequest: CaptureRequest? = null
    private var imageReader: ImageReader? = null

    private var state = CaptureState.CLOSED
        set(value) {
            if (value == field) return
            logger.d("capture state changed from `$field` to `$value`")
            field = value
        }

    private val logger = XLog.tag("headlessCapture").build()

    private var cameraStateCallback = object : CameraDevice.StateCallback() {
        private val tag = "cameraService/cameraStateCallback"

        override fun onOpened(camera: CameraDevice) {
            logger.d("onOpened")
            
            state = CaptureState.CAMERA_OPENED

            val surface = imageReader?.surface ?: run {
                logger.d("imageReader surface is empty")
                return
            }

            try {
                camera.apply {
                    createCaptureSession(listOf(surface), sessionStateCallback, null)

//                    val focusAreaTouch = MeteringRectangle(
//                        1000, 1000, 2000, 2000,
//                        MeteringRectangle.METERING_WEIGHT_MAX - 1
//                    )
//https://gist.github.com/royshil/8c760c2485257c85a11cafd958548482
                    cameraRequest =
                        createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(surface)
                            set(
                                CaptureRequest.CONTROL_MODE,
                                CameraMetadata.CONTROL_MODE_AUTO
                            )
                            set(
                                CaptureRequest.CONTROL_AWB_MODE,
                                CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                            )
//                            set(
//                                CaptureRequest.CONTROL_AF_REGIONS,
//                                arrayOf(focusAreaTouch)
//                            )
                        }.build()
                }
            } catch (e: CameraAccessException) {
                logger.e("camera session and request creation failed", e)
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            logger.d("CameraDevice.StateCallback onDisconnected")
            state = CaptureState.CLOSED
        }

        override fun onError(camera: CameraDevice, error: Int) {
            logger.d("CameraDevice.StateCallback onError $error")
            state = CaptureState.CLOSED
        }
    }

    private var sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        private val tag = "cameraService/sessionStateCallback"

        override fun onReady(session: CameraCaptureSession) {
            when (state) {
                CaptureState.CAPTURED -> {
                    session.close()
                    logger.d("already processed. Breaking")
                }
                CaptureState.CLOSED -> {
                    return
                }
                else -> {
                    val request = cameraRequest ?: run {
                        logger.d("cameraRequest is empty")
                        return
                    }

                    try {
                        session.capture(request, null, null)
                        state = CaptureState.SESSION_OPENED
                        logger.d("capture requested")
                    } catch (e: CameraAccessException) {
                        logger.e("session capture request", e)
                    }
                }
            }


        }

        override fun onConfigured(session: CameraCaptureSession) {
            logger.d("onConfigured")
            cameraCaptureStartTime = System.currentTimeMillis()
        }

        override fun onClosed(session: CameraCaptureSession) {
            logger.d("onClosed")
            state = CaptureState.CLOSED
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {}
    }

    private var onImageAvailableListener = OnImageAvailableListener { reader ->
        val latestImage = reader.acquireLatestImage()

        val img = latestImage.takeIf { it.format == ImageFormat.JPEG } ?: run {
            latestImage?.close()
            logger.d("last acquired frame is empty or has non JPEG")
            return@OnImageAvailableListener
        }

        if (System.currentTimeMillis() < cameraCaptureStartTime + delay || state == CaptureState.CAPTURED) {
            img.close()
            logger.d("it is not valid time or already processed")
            return@OnImageAvailableListener
        }

        state = CaptureState.CAPTURED

        captureCallback.get()?.onCaptured(img.planes[0].buffer)
        img.close()
    }

/* methods */

    fun takePhoto() {
        if (state != CaptureState.CLOSED) return

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = manager.cameraIdList.firstOrNull {
                manager
                    .getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: run {
                logger.d("not found back camera id")
                return
            }

            if (
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                logger.d("no permission to use camera")
                return
            }

//            val characteristics = manager.getCameraCharacteristics(cameraId)
//            logger.d(characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE).toString())
//            logger.d(characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF).toString())

            manager.openCamera(cameraId, cameraStateCallback, null)

            state = CaptureState.CLOSED

            imageReader = ImageReader.newInstance(4032, 3024, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener(onImageAvailableListener, null)
            }

            logger.d("imageReader created")
        } catch (e: CameraAccessException) {
            logger.e("failed to open camera", e)
        }
    }

}
