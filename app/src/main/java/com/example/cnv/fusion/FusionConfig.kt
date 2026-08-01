package com.example.cnv.fusion

/**
 * Tunable fusion parameters. All magic numbers live here.
 */
data class FusionConfig(
    val timeWindowNs: Long = DEFAULT_TIME_WINDOW_NS,
    val maximumDelayNs: Long = DEFAULT_MAXIMUM_DELAY_NS,
    val minimumConfidence: Float = DEFAULT_MINIMUM_CONFIDENCE,
    val minimumTrackingCount: Int = DEFAULT_MINIMUM_TRACKING_COUNT,
    val distanceWeight: Float = DEFAULT_DISTANCE_WEIGHT,
    val shockWeight: Float = DEFAULT_SHOCK_WEIGHT,
    val calibrationWeight: Float = DEFAULT_CALIBRATION_WEIGHT,
    val trackingWeight: Float = DEFAULT_TRACKING_WEIGHT,
    val peakAccelerationWeight: Float = DEFAULT_PEAK_ACCELERATION_WEIGHT,
    val ransacWeight: Float = DEFAULT_RANSAC_WEIGHT,
    val trackingCountNorm: Float = DEFAULT_TRACKING_COUNT_NORM,
    val peakAccelerationNorm: Float = DEFAULT_PEAK_ACCELERATION_NORM,
    val historyCapacity: Int = DEFAULT_HISTORY_CAPACITY,
    val debugHudRefreshIntervalMs: Long = DEFAULT_DEBUG_HUD_REFRESH_MS,
) {
    companion object {
        /** Match Distance/Shock only if |Δt| ≤ this window. */
        const val DEFAULT_TIME_WINDOW_NS = 50_000_000L // 50 ms

        /** Drop buffered events older than this. */
        const val DEFAULT_MAXIMUM_DELAY_NS = 200_000_000L // 200 ms

        const val DEFAULT_MINIMUM_CONFIDENCE = 0.25f
        const val DEFAULT_MINIMUM_TRACKING_COUNT = 8

        const val DEFAULT_DISTANCE_WEIGHT = 0.30f
        const val DEFAULT_SHOCK_WEIGHT = 0.25f
        const val DEFAULT_CALIBRATION_WEIGHT = 0.15f
        const val DEFAULT_TRACKING_WEIGHT = 0.10f
        const val DEFAULT_PEAK_ACCELERATION_WEIGHT = 0.10f
        const val DEFAULT_RANSAC_WEIGHT = 0.10f

        const val DEFAULT_TRACKING_COUNT_NORM = 40f
        const val DEFAULT_PEAK_ACCELERATION_NORM = 25f

        const val DEFAULT_HISTORY_CAPACITY = 64
        const val DEFAULT_DEBUG_HUD_REFRESH_MS = 200L

        val DEFAULT: FusionConfig = FusionConfig()
    }
}
