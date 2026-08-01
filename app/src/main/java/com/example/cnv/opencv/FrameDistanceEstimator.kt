package com.example.cnv.opencv

import com.example.cnv.config.CalibrationManager

/**
 * Converts median pixel movement to millimeters using [CalibrationManager].
 */
class FrameDistanceEstimator(
    private val calibrationManager: CalibrationManager,
) {

    fun toMm(medianPixel: Float): Float {
        if (!calibrationManager.isCalibrated()) {
            return 0f
        }
        return medianPixel * calibrationManager.getMmPerPixel()
    }
}
