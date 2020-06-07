package com.shibuiwilliam.tflitepytorch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PersistableBundle
import android.util.Log
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

abstract class AbstractCameraXActivity : AppCompatActivity(){
    private val TAG: String = AbstractCameraXActivity::class.java.simpleName

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf<String>(
        Manifest.permission.CAMERA
    )
    protected var mBackgroundThread: HandlerThread? = null
    protected var mBackgroundHandler: Handler? = null

//    protected abstract fun getContentView(): Int
//    protected abstract fun getTextureView(): TextureView
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(getContentView())
//
//
//    }

    protected fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("BackgroundThread")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    protected fun stopBackgroundThread() {
        mBackgroundThread!!.quitSafely()
        try {
            mBackgroundThread!!.join()
            mBackgroundThread = null
            mBackgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error on stopping background thread", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (!allPermissionsGranted()) {
                finish()
            }
        }
    }

    protected fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
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
}