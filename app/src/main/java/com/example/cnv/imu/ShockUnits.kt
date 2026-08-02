package com.example.cnv.imu

/**
 * Shock unit helpers. Detection works in m/s²; storage / HeatMap / Export use g.
 *
 * Record  = 1.03 g
 * Warning = 1.06 g
 * Critical = 1.20 g
 */
object ShockUnits {
    const val STANDARD_GRAVITY_MS2 = 9.80665f

    /** Record Threshold — ≥ this must be recorded (HeatMap / Peak / Export). */
    const val RECORDING_THRESHOLD_G = 1.03f

    /** Warning Threshold — yellow / elevated band. */
    const val WARNING_THRESHOLD_G = 1.06f

    /** Critical Threshold — red / critical band. */
    const val CRITICAL_THRESHOLD_G = 1.20f

    /** Soft orange band between Warning and Critical (display only). */
    const val HIGH_THRESHOLD_G = 1.13f

    // Legacy aliases used by HeatMapIntensityConfig.
    const val BAND_GREEN_MAX_G = WARNING_THRESHOLD_G
    const val BAND_YELLOW_MAX_G = HIGH_THRESHOLD_G
    const val BAND_ORANGE_MAX_G = CRITICAL_THRESHOLD_G

    /** Moving-average window for shock stats (samples). */
    const val MOVING_AVERAGE_WINDOW_MIN = 5
    const val MOVING_AVERAGE_WINDOW_MAX = 10
    const val MOVING_AVERAGE_WINDOW_DEFAULT = 8

    /** Target IMU sampling (~100 Hz → 10_000 µs). */
    const val TARGET_SAMPLING_HZ = 100

    /** Minimum Event Duration = 20 ms. */
    const val MIN_EVENT_DURATION_NS = 20_000_000L

    /** Event Merge Window = 100 ms. */
    const val EVENT_MERGE_WINDOW_NS = 100_000_000L

    fun ms2ToG(ms2: Float): Float = ms2 / STANDARD_GRAVITY_MS2

    fun gToMs2(g: Float): Float = g * STANDARD_GRAVITY_MS2

    fun recordingThresholdMs2(): Float = gToMs2(RECORDING_THRESHOLD_G)

    fun warningThresholdMs2(): Float = gToMs2(WARNING_THRESHOLD_G)

    fun criticalThresholdMs2(): Float = gToMs2(CRITICAL_THRESHOLD_G)

    fun isRecordableG(shockG: Float): Boolean = shockG >= RECORDING_THRESHOLD_G

    fun isWarningG(shockG: Float): Boolean = shockG >= WARNING_THRESHOLD_G

    fun isCriticalG(shockG: Float): Boolean = shockG >= CRITICAL_THRESHOLD_G

    /**
     * Normalize a profile / UI threshold that may be stored as g or m/s² into g.
     */
    fun asThresholdG(raw: Float): Float {
        if (raw <= 0f) return RECORDING_THRESHOLD_G
        return if (raw > 4f) ms2ToG(raw) else raw
    }

    fun clampMovingAverageWindow(window: Int): Int =
        window.coerceIn(MOVING_AVERAGE_WINDOW_MIN, MOVING_AVERAGE_WINDOW_MAX)
}
