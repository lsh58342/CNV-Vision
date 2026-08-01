package com.example.cnv.opencv

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.opencv.android.OpenCVLoader

class OpenCVViewModel : ViewModel() {

    private val _grayFrame = MutableLiveData<Bitmap?>()
    val grayFrame: LiveData<Bitmap?> = _grayFrame

    private val _distanceResult = MutableLiveData<DistanceEstimateResult?>()
    val distanceResult: LiveData<DistanceEstimateResult?> = _distanceResult

    private var initialized: Boolean = false

    fun initialize(): Boolean {
        if (initialized) {
            return true
        }
        initialized = OpenCVLoader.initLocal()
        return initialized
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Posts a UI-owned Bitmap. Analyzer must pass a copy; ViewModel does not recycle here —
     * ImageView observer recycles the previous frame after swap.
     */
    fun publishGrayFrame(bitmap: Bitmap) {
        _grayFrame.postValue(bitmap)
    }

    fun publishDistanceResult(result: DistanceEstimateResult) {
        _distanceResult.postValue(result)
    }

    fun clearFrames() {
        _grayFrame.postValue(null)
        _distanceResult.postValue(null)
    }
}
