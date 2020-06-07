package com.shibuiwilliam.tflitepytorch

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.Nullable
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Preview.OnPreviewOutputUpdateListener
import androidx.camera.core.Preview.PreviewOutput
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


abstract class AbstractCameraXActivity : AppCompatActivity() {
    private val TAG: String = AbstractCameraXActivity::class.java.simpleName

    protected var mBackgroundThread: HandlerThread? = null
    protected var mBackgroundHandler: Handler? = null

    private var mLastAnalysisResultTime: Long = 0
    protected abstract val contentViewLayoutId: Int
    protected abstract val cameraPreviewTextureView: TextureView

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf<String>(
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentViewLayoutId)
        startBackgroundThread()

        if (allPermissionsGranted()){
            setupCameraX()
        }
        else{
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS)
        }
    }

    protected open fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("BackgroundThread")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    protected open fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error on stopping background thread", e)
        }
    }

    private fun setupCameraX() {
        CameraX.unbindAll()
        val textureView = cameraPreviewTextureView
        val screenSize = Size(textureView.width, textureView.height)
        val screenAspectRatio = Rational(1,1)
        val previewConfig = PreviewConfig
            .Builder()
            .apply{
                setLensFacing(CameraX.LensFacing.BACK)
                setTargetResolution(screenSize)
                setTargetAspectRatio(screenAspectRatio)
                setTargetRotation(windowManager.defaultDisplay.rotation)
            }
            .build()
        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {it ->
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            textureView.surfaceTexture = it.surfaceTexture
            parent.addView(textureView, 0)
            updateTransform()
        }

        val imageAnalysisConfig = ImageAnalysisConfig
            .Builder()
            .apply{
                setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            }
            .build()

        val imageAnalysis = ImageAnalysis(imageAnalysisConfig)
        imageAnalysis.analyzer =
            ImageAnalysis.Analyzer { image: ImageProxy?, rotationDegrees: Int -> }

        val imageCaptureConfig = ImageCaptureConfig
            .Builder()
            .apply {
                setTargetAspectRatio(screenAspectRatio)
                setTargetRotation(windowManager.defaultDisplay.rotation)
                setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
            }
            .build()
        val imageCapture = ImageCapture(imageCaptureConfig)

        CameraX.bindToLifecycle(this, imageCapture, imageAnalysis, preview)
    }

    private fun updateTransform() {
        val matrix = Matrix()
        val centerX = cameraPreviewTextureView.width / 2f
        val centerY = cameraPreviewTextureView.height / 2f

        val rotationDegrees = when (cameraPreviewTextureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        cameraPreviewTextureView.setTransform(matrix)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        Log.i(TAG, "Permitted to use camera and internet")
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundThread()
    }

    @WorkerThread
    @Nullable
    protected abstract fun analyzeImage(image: ImageProxy?, rotationDegrees: Int): R

    @UiThread
    protected abstract fun applyToUiAnalyzeImageResult(result: R)
}