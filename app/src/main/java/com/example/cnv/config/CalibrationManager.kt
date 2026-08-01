package com.example.cnv.config

import android.content.Context

/**
 * App-wide calibration authority for mmPerPixel.
 * DistanceEstimator must only read via this manager — never compute scale itself.
 */
class CalibrationManager private constructor(
    private val repository: CalibrationRepository,
) {

    @Volatile
    private var cached: CalibrationData? = repository.loadCalibration()

    @Volatile
    private var lastObservedPixelDistance: Float = 0f

    fun getMmPerPixel(): Float = cached?.mmPerPixel ?: 0f

    fun setMmPerPixel(mmPerPixel: Float) {
        require(mmPerPixel > 0f) { "mmPerPixel must be positive" }
        val data = CalibrationData(
            mmPerPixel = mmPerPixel,
            calibratedAt = System.currentTimeMillis(),
            version = CalibrationData.CURRENT_VERSION,
        )
        repository.saveCalibration(data)
        cached = data
    }

    fun calculateMmPerPixel(realDistanceMm: Float, pixelDistance: Float): Float {
        require(realDistanceMm > 0f) { "realDistanceMm must be positive" }
        require(pixelDistance > 0f) { "pixelDistance must be positive" }
        return realDistanceMm / pixelDistance
    }

    fun isCalibrated(): Boolean {
        val value = cached?.mmPerPixel ?: return false
        return value > 0f
    }

    fun getCalibrationData(): CalibrationData? = cached

    fun resetCalibration() {
        repository.resetCalibration()
        cached = null
    }

    fun reload() {
        cached = repository.loadCalibration()
    }

    /**
     * Latest median/consensus pixel motion observed by the distance pipeline.
     */
    fun updateObservedPixelDistance(pixelDistance: Float) {
        if (pixelDistance >= 0f) {
            lastObservedPixelDistance = pixelDistance
        }
    }

    fun getLastPixelDistance(): Float = lastObservedPixelDistance

    companion object {
        @Volatile
        private var instance: CalibrationManager? = null

        fun getInstance(context: Context): CalibrationManager {
            return instance ?: synchronized(this) {
                instance ?: CalibrationManager(
                    CalibrationRepository(context.applicationContext),
                ).also { instance = it }
            }
        }
    }
}
