package com.shibuiwilliam.tflitepytorch

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.TextureView
import androidx.camera.core.ImageProxy
import com.shibuiwilliam.tflitepytorch.R

class TFLiteActivity : AbstractCameraXActivity() {
    private val TAG: String = TFLiteActivity::class.java.simpleName

    private var app: App? = null
    override val contentViewLayoutId: Int = R.layout.activity_t_f_lite
    override val cameraPreviewTextureView: TextureView = findViewById(R.id.cameraPreviewTextureView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentViewLayoutId)

        if (app == null){
            app = application as App
        }
    }

    override fun analyzeImage(image: ImageProxy?, rotationDegrees: Int): R {
        TODO("Not yet implemented")
    }

    override fun applyToUiAnalyzeImageResult(result: R) {
        TODO("Not yet implemented")
    }
}
