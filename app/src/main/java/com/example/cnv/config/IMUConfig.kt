package com.example.cnv.config

/**
 * Tunable IMU / shock-detection parameters. No magic numbers in IMU code paths.
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
        /** ~100 Hz game-rate sampling. */
        const val DEFAULT_SAMPLING_PERIOD_US = 10_000

        /** Gravity low-pass coefficient (0..1). Higher = slower gravity adapt. */
        const val DEFAULT_LOW_PASS_ALPHA = 0.8f

        /** Linear-accel high-pass coefficient (0..1). */
        const val DEFAULT_HIGH_PASS_ALPHA = 0.1f

        /** Peak linear acceleration magnitude (m/s^2) to start a shock candidate. */
        const val DEFAULT_SHOCK_ACCEL_THRESHOLD = 12.0f

        /** Peak gyro magnitude (rad/s) contributing to shock score. */
        const val DEFAULT_SHOCK_GYRO_THRESHOLD = 2.5f

        /** Minimum sustained peak window for a valid shock. */
        const val DEFAULT_PEAK_DURATION_NS = 30_000_000L

        /** Minimum confidence to publish ShockEvent. */
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.55f

        /** Ignore linear-accel peaks below this noise floor (m/s^2). */
        const val DEFAULT_NOISE_FLOOR_LINEAR_ACCEL = 0.5f

        val DEFAULT: IMUConfig = IMUConfig()
    }
}
