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

    internal lateinit var pytorchModule: Module

    override fun onCreate() {
        super.onCreate()
    }

    internal fun initialize(activity: Activity, device: Constants.Device, numThreads: Int){
        labels = Utils.loadLabelList(activity, Constants.LABEL_PATH)

        initializePyTorch(activity)
    }

    internal fun initializePyTorch(activity: Activity){
        pytorchModule =
            Module.load(Utils.assetFilePath(activity, Constants.PYTORCH_MOBILENET_QUANTIZED_PATH))
    }

    internal fun close() {
    }
}