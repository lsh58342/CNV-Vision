package com.example.cnv.core.config

/**
 * Tunable IMU / shock-detection parameters.
 */
data class IMUConfig(
    val samplingPeriodUs: Int = DEFAULT_SAMPLING_PERIOD_US,
    val lowPassAlpha: Float = DEFAULT_LOW_PASS_ALPHA,
    val highPassAlpha: Float = DEFAULT_HIGH_PASS_ALPHA,
    val shockAccelerationThreshold: Float = DEFAULT_SHOCK_ACCEL_THRESHOLD,
    val shockGyroscopeThreshold: Float = DEFAULT_SHOCK_GYRO_THRESHOLD,
    val peakDurationNs: Long = DEFAULT_PEAK_DURATION_NS,
    val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    val noiseFloorLinearAccel: Float = DEFAULT_NOISE_FLOOR_LINEAR_ACCEL,
) {
    companion object {
        const val DEFAULT_SAMPLING_PERIOD_US = 10_000
        const val DEFAULT_LOW_PASS_ALPHA = 0.8f
        const val DEFAULT_HIGH_PASS_ALPHA = 0.1f
        const val DEFAULT_SHOCK_ACCEL_THRESHOLD = 12.0f
        const val DEFAULT_SHOCK_GYRO_THRESHOLD = 2.5f
        const val DEFAULT_PEAK_DURATION_NS = 30_000_000L
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.55f
        const val DEFAULT_NOISE_FLOOR_LINEAR_ACCEL = 0.5f

        val DEFAULT: IMUConfig = IMUConfig()
    }
}
