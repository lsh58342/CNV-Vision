package com.example.cnv.opencv

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.opencv.android.OpenCVLoader

class OpenCVViewModel : ViewModel() {

    private val _grayFrame = MutableLiveData<Bitmap>()
    val grayFrame: LiveData<Bitmap> = _grayFrame

    private val _movementDistancePx = MutableLiveData(0f)
    val movementDistancePx: LiveData<Float> = _movementDistancePx

    private var initialized: Boolean = false

    fun initialize(): Boolean {
        if (initialized) {
            return true
        }
        initialized = OpenCVLoader.initLocal()
        return initialized
    }

    fun isInitialized(): Boolean = initialized

    fun publishGrayFrame(bitmap: Bitmap) {
        _grayFrame.postValue(bitmap)
    }

    fun publishMovementDistance(distancePx: Float) {
        _movementDistancePx.postValue(distancePx)
    }
}
