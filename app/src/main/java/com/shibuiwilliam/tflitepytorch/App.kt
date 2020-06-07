package com.shibuiwilliam.tflitepytorch

import android.app.Activity
import android.app.Application
import org.pytorch.Module
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.MappedByteBuffer


class App: Application(){
    private val TAG: String = App::class.java.simpleName

    internal lateinit var labels: List<String>

    private lateinit var tfliteModel: MappedByteBuffer
    internal lateinit var tfliteInterpreter: Interpreter
    private val tfliteOptions = Interpreter.Options()
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null

    internal lateinit var pytorchModule: Module

    override fun onCreate() {
        super.onCreate()
    }

    internal fun initialize(activity: Activity, device: Constants.Device, numThreads: Int){
        labels = Utils.loadLabelList(activity, Constants.LABEL_PATH)

        initializeTFLite(activity, device, numThreads)
        initializePyTorch(activity)
    }

    internal fun initializeTFLite(activity: Activity, device: Constants.Device, numThreads: Int){
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
        tfliteModel = FileUtil.loadMappedFile(activity, Constants.TFLITE_MOBILENET_V2_PATH)
        tfliteInterpreter = Interpreter(tfliteModel, tfliteOptions)
    }

    internal fun initializePyTorch(activity: Activity){
        pytorchModule =
            Module.load(Utils.assetFilePath(activity, Constants.PYTORCH_MOBILENET_QUANTIZED_PATH))
    }

    internal fun close() {
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