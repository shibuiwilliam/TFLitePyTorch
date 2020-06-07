package com.shibuiwilliam.tflitepytorch

object Constants{
    const val LabelPath = "imagenet_labels.txt"
    const val TFLiteMobileNetV2Path = "mobilenet_v2_1.0_224.tflite"
    const val PyTorchMobileNetQuantizedPath = "mobilenet_quantized_scripted_925.pt"

    enum class Device{
        CPU,
        NNAPI,
        GPU
    }
}