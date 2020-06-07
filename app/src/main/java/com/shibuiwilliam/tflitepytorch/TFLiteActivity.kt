package com.shibuiwilliam.tflitepytorch

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer


class TFLiteActivity : AbstractCameraXActivity() {
    private val TAG: String = TFLiteActivity::class.java.simpleName

    private var app: App? = null
    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf<String>(
        Manifest.permission.CAMERA
    )
//    protected var mBackgroundThread: HandlerThread? = null
//    protected var mBackgroundHandler: Handler? = null

    lateinit var textureView: TextureView
    lateinit var textView: TextView

    private lateinit var inputImageBuffer: TensorImage
    private lateinit var outputProbabilityButter: TensorBuffer
    private lateinit var probabilityProcessor: TensorProcessor

    private var mLastAnalysisResultTime: Long = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_t_f_lite)

        textureView = findViewById(R.id.cameraPreviewTextureView)
        textView = findViewById(R.id.inferenceText)

        if (app==null) app = application as App

        probabilityProcessor = TensorProcessor
            .Builder()
            .add(getPostprocessNormalizeOp())
            .build()

        inputImageBuffer = TensorImage(app!!.tfliteInterpreter.getInputTensor(0).dataType())
        outputProbabilityButter = TensorBuffer.createFixedSize(
            app!!.tfliteInterpreter.getOutputTensor(0).shape(),
            app!!.tfliteInterpreter.getInputTensor(0).dataType()
        )


        if (allPermissionsGranted()) {
            startBackgroundThread()
            textureView.post { setupCameraX() }
            textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateTransform()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun setupCameraX() {
        CameraX.unbindAll()

        val screenSize = Size(textureView.width, textureView.height)
        val screenAspectRatio = Rational(1, 1)

        val previewConfig = PreviewConfig
            .Builder()
            .apply {
                setLensFacing(CameraX.LensFacing.BACK)
                setTargetResolution(screenSize)
                setTargetAspectRatio(screenAspectRatio)
                setTargetRotation(windowManager.defaultDisplay.rotation)
            }
            .build()
        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            textureView.surfaceTexture = it.surfaceTexture
            parent.addView(textureView, 0)
        }

        val imageAnalysisConfig = ImageAnalysisConfig
            .Builder()
            .apply {
                setCallbackHandler(mBackgroundHandler)
                setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            }
            .build()

        val imageAnalysis = ImageAnalysis(imageAnalysisConfig)
        imageAnalysis.analyzer =
            ImageAnalysis.Analyzer { image: ImageProxy?, rotationDegrees: Int ->
                if (System.currentTimeMillis() - mLastAnalysisResultTime < 500) return@Analyzer
                if (image == null) return@Analyzer
                analyzeImage(image, rotationDegrees)
                mLastAnalysisResultTime = System.currentTimeMillis()
            }

        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }


    fun analyzeImage(image: ImageProxy, rotationDegrees: Int) {
        var bitmap = Utils.imageToBitmap(image)
        bitmap = rotateBitmap(bitmap, 90f)
        val labeledProbability = classifyImage(bitmap)
        Log.i(TAG, "prediction: ${labeledProbability}")
    }

    private fun classifyImage(bitmap: Bitmap): Map<String, Float> {
        val inputImageBuffer = loadImage(bitmap)
        app!!.tfliteInterpreter.run(
            inputImageBuffer!!.buffer,
            outputProbabilityButter.buffer.rewind()
        )
        val labeledProbability: MutableMap<String, Float> = TensorLabel(
            app!!.labels,
            probabilityProcessor.process(outputProbabilityButter)
        ).mapWithFloatValue
        return labeledProbability
    }

    private fun loadImage(bitmap: Bitmap): TensorImage? {
        inputImageBuffer.load(bitmap)

        val cropSize = Math.min(bitmap.width, bitmap.height)

        val imageProcessor: ImageProcessor = ImageProcessor.Builder()
            .add(ResizeWithCropOrPadOp(cropSize, cropSize))
            .add(
                ResizeOp(
                    Constants.INPUT_IMAGE_SIZE,
                    Constants.INPUT_IMAGE_SIZE,
                    ResizeMethod.NEAREST_NEIGHBOR
                )
            )
            .add(getPreprocessNormalizeOp())
            .build()
        return imageProcessor.process(inputImageBuffer)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun getPreprocessNormalizeOp(): TensorOperator? {
        return NormalizeOp(Constants.IMAGE_MEAN, Constants.IMAGE_STD)
    }

    protected fun getPostprocessNormalizeOp(): TensorOperator? {
        return NormalizeOp(Constants.PROBABILITY_MEAN, Constants.PROBABILITY_STD)
    }

    private fun updateTransform() {
        val matrix = Matrix()
        val centerX = textureView.width / 2f
        val centerY = textureView.height / 2f

        val rotationDegrees = when (textureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        textureView.setTransform(matrix)
    }

//    protected fun startBackgroundThread() {
//        mBackgroundThread = HandlerThread("BackgroundThread")
//        mBackgroundThread!!.start()
//        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
//    }
//
//    protected fun stopBackgroundThread() {
//        mBackgroundThread!!.quitSafely()
//        try {
//            mBackgroundThread!!.join()
//            mBackgroundThread = null
//            mBackgroundHandler = null
//        } catch (e: InterruptedException) {
//            Log.e(TAG, "Error on stopping background thread", e)
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray
//    ) {
//        if (requestCode == REQUEST_CODE_PERMISSIONS) {
//            if (!allPermissionsGranted()) {
//                finish()
//            }
//        }
//    }
//
//    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
//        for (permission in REQUIRED_PERMISSIONS) {
//            if (ContextCompat.checkSelfPermission(this, permission)
//                != PackageManager.PERMISSION_GRANTED
//            ) {
//                return false
//            }
//        }
//        Log.i(TAG, "Permitted to use camera and internet")
//        return true
//    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundThread()
    }
}
