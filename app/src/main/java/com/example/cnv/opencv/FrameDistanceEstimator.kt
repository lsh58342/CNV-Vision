package com.example.cnv.opencv

/**
 * Converts median pixel movement to millimeters.
 */
class FrameDistanceEstimator(
    private val calibrator: DistanceCalibrator,
) {

    fun toMm(medianPixel: Float): Float {
        if (!calibrator.isCalibrated()) {
            return 0f
        }
        return medianPixel * calibrator.mmPerPixel()
    }
}
