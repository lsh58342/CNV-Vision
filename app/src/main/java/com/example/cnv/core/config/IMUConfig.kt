package com.example.cnv.core.config

import com.example.cnv.imu.ShockUnits

/**
 * Tunable IMU / shock-detection parameters.
 *
 * Defaults: Record 1.03g, sample ~100 Hz, min duration 20 ms, merge 100 ms.
 */
data class IMUConfig(
    val samplingPeriodUs: Int = DEFAULT_SAMPLING_PERIOD_US,
    val lowPassAlpha: Float = DEFAULT_LOW_PASS_ALPHA,
    val highPassAlpha: Float = DEFAULT_HIGH_PASS_ALPHA,
    val shockAccelerationThreshold: Float = DEFAULT_SHOCK_ACCEL_THRESHOLD,
    val shockGyroscopeThreshold: Float = DEFAULT_SHOCK_GYRO_THRESHOLD,
    val peakDurationNs: Long = DEFAULT_PEAK_DURATION_NS,
    val eventMergeWindowNs: Long = DEFAULT_EVENT_MERGE_WINDOW_NS,
    val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    val noiseFloorLinearAccel: Float = DEFAULT_NOISE_FLOOR_LINEAR_ACCEL,
) {
    companion object {
        /** ~100 Hz. */
        const val DEFAULT_SAMPLING_PERIOD_US = 10_000
        const val DEFAULT_LOW_PASS_ALPHA = 0.8f
        const val DEFAULT_HIGH_PASS_ALPHA = 0.1f
        /** Record Threshold 1.03 g in m/s². */
        val DEFAULT_SHOCK_ACCEL_THRESHOLD: Float = ShockUnits.recordingThresholdMs2()
        const val DEFAULT_SHOCK_GYRO_THRESHOLD = 2.5f
        /** Minimum Event Duration = 20 ms. */
        val DEFAULT_PEAK_DURATION_NS: Long = ShockUnits.MIN_EVENT_DURATION_NS
        /** Event Merge Window = 100 ms. */
        val DEFAULT_EVENT_MERGE_WINDOW_NS: Long = ShockUnits.EVENT_MERGE_WINDOW_NS
        /** Soft gate; peaks ≥ recording threshold always pass (see ShockDetector). */
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.40f
        const val DEFAULT_NOISE_FLOOR_LINEAR_ACCEL = 0.35f

        val DEFAULT: IMUConfig = IMUConfig()
    }
}
