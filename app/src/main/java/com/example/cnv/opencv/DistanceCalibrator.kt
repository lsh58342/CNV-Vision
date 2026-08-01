package com.example.cnv.opencv

/**
 * Holds mm-per-pixel scale. Interactive calibration UX is TODO.
 */
class DistanceCalibrator(
    private var mmPerPixel: Float = DEFAULT_MM_PER_PIXEL,
    private var calibrated: Boolean = true,
) {

    fun mmPerPixel(): Float = mmPerPixel

    fun isCalibrated(): Boolean = calibrated && mmPerPixel > 0f

    fun setMmPerPixel(value: Float) {
        // TODO: wire Settings / two-point calibration UX (DistanceEstimation.md §8)
        mmPerPixel = value
        calibrated = value > 0f
    }

    companion object {
        /**
         * Provisional default until real L_mm / L_px calibration is performed.
         * TODO: replace via DistanceCalibrator.setMmPerPixel after measurement.
         */
        const val DEFAULT_MM_PER_PIXEL = 0.1f
    }
}
