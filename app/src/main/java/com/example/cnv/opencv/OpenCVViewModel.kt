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

    /**
     * Bitmap posted via [publishGrayFrame] that has not yet been delivered to observers.
     * LiveData coalesces posts — superseded pending frames must be recycled here.
     */
    @Volatile
    private var pendingGray: Bitmap? = null

    /** Last bitmap known to be held by the ImageView (delivered + displayed). */
    @Volatile
    private var displayedGray: Bitmap? = null

    private val bitmapLock = Any()

    fun initialize(): Boolean {
        if (initialized) {
            return true
        }
        initialized = OpenCVLoader.initLocal()
        return initialized
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Posts a UI-owned Bitmap copy from the Analyzer.
     * Ownership: pending until observer displays; recycle orphans when superseded.
     */
    fun publishGrayFrame(bitmap: Bitmap) {
        synchronized(bitmapLock) {
            val previousPending = pendingGray
            if (previousPending != null &&
                previousPending !== bitmap &&
                !previousPending.isRecycled
            ) {
                previousPending.recycle()
            }
            pendingGray = bitmap
        }
        _grayFrame.postValue(bitmap)
    }

    /**
     * Called after ImageView swap. Clears pending ownership for [bitmap].
     */
    fun onGrayFrameDisplayed(bitmap: Bitmap?) {
        synchronized(bitmapLock) {
            if (bitmap != null && pendingGray === bitmap) {
                pendingGray = null
            }
            displayedGray = bitmap
        }
    }

    fun publishDistanceResult(result: DistanceEstimateResult) {
        _distanceResult.postValue(result)
    }

    fun clearFrames() {
        synchronized(bitmapLock) {
            val pending = pendingGray
            pendingGray = null
            displayedGray = null
            if (pending != null && !pending.isRecycled) {
                pending.recycle()
            }
        }
        _grayFrame.postValue(null)
        _distanceResult.postValue(null)
    }

    override fun onCleared() {
        clearFrames()
        super.onCleared()
    }
}
