package com.shibuiwilliam.tflitepytorch

import android.Manifest
import android.app.Activity
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
import androidx.annotation.Nullable
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
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
import java.lang.Exception
import java.nio.MappedByteBuffer
import java.util.*


class TFLiteActivity : AbstractCameraXActivity() {
    private val TAG: String = TFLiteActivity::class.java.simpleName

    private lateinit var tfliteModel: MappedByteBuffer
    internal lateinit var tfliteInterpreter: Interpreter
    private val tfliteOptions = Interpreter.Options()
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null

    private lateinit var inputImageBuffer: TensorImage
    private lateinit var outputProbabilityButter: TensorBuffer
    private lateinit var probabilityProcessor: TensorProcessor


    override fun getContentView(): Int = R.layout.activity_t_f_lite
    override fun getCameraTextureView(): TextureView = findViewById(R.id.cameraPreviewTextureView)
    override fun getInferenceTextView(): TextView = findViewById(R.id.inferenceText)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeTFLite(Constants.Device.NNAPI, Constants.NUM_THREAD)

        probabilityProcessor = TensorProcessor
            .Builder()
            .add(postprocessNormalizeOp())
            .build()

        inputImageBuffer = TensorImage(tfliteInterpreter.getInputTensor(0).dataType())
        outputProbabilityButter = TensorBuffer.createFixedSize(
            tfliteInterpreter.getOutputTensor(0).shape(),
            tfliteInterpreter.getInputTensor(0).dataType()
        )
    }

    internal fun initializeTFLite(device: Constants.Device, numThreads: Int){
        when (device) {
            Constants.Device.NNAPI -> {
                nnApiDelegate = NnApiDelegate()
                tfliteOptions.addDelegate(nnApiDelegate)
            }
            Constants.Device.GPU -> {
                gpuDelegate = GpuDelegate()
                tfliteOptions.addDelegate(gpuDelegate)
            }
            Constants.Device.CPU -> {
            }
        }
        tfliteOptions.setNumThreads(numThreads)
        tfliteModel = FileUtil.loadMappedFile(this, Constants.TFLITE_MOBILENET_V2_PATH)
        tfliteInterpreter = Interpreter(tfliteModel, tfliteOptions)
    }

    @WorkerThread
    @Nullable
    override fun analyzeImage(image: ImageProxy, rotationDegrees: Int): String? {
        try {
            var bitmap = Utils.imageToBitmap(image)
            bitmap = rotateBitmap(bitmap, 90f)
            val labeledProbability = classifyImage(bitmap)
            Log.i(TAG, "prediction: ${labeledProbability}")
            return labeledProbability.map{it ->
                "${it.key}: ${it.value} \n"
            }.joinToString()
        }
        catch (e: Exception){
            e.printStackTrace()
            return null
        }
    }

    @UiThread
    override fun showResult(result: String) {
        textView.text = result
    }

    private fun classifyImage(bitmap: Bitmap): Map<String, Float> {
        val inputImageBuffer = loadImage(bitmap)
        tfliteInterpreter.run(
            inputImageBuffer!!.buffer,
            outputProbabilityButter.buffer.rewind()
        )
        val labeledProbability: MutableMap<String, Float> = TensorLabel(
            app!!.labels,
            probabilityProcessor.process(outputProbabilityButter)
        ).mapWithFloatValue
        return Utils.prioritizeByProbability(labeledProbability)
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
            .add(preprocessNormalizeOp())
            .build()
        return imageProcessor.process(inputImageBuffer)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun preprocessNormalizeOp(): TensorOperator? {
        return NormalizeOp(Constants.IMAGE_MEAN, Constants.IMAGE_STD)
    }

    protected fun postprocessNormalizeOp(): TensorOperator? {
        return NormalizeOp(Constants.PROBABILITY_MEAN, Constants.PROBABILITY_STD)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundThread()

        if (::tfliteInterpreter.isInitialized) {
            tfliteInterpreter.close()
        }
        if (gpuDelegate != null) {
            gpuDelegate!!.close()
            gpuDelegate = null
        }
        if (nnApiDelegate != null) {
            nnApiDelegate!!.close()
            nnApiDelegate = null
        }
    }
}
