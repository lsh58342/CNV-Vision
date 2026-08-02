package com.example.cnv.imu

/**
 * Shock unit helpers (STEP 20-20). Detection works in m/s²; storage / HeatMap / Export use g.
 */
object ShockUnits {
    const val STANDARD_GRAVITY_MS2 = 9.80665f

    /** Recording / HeatMap / Export gate — ≥ 1.10 g must be recorded. */
    const val RECORDING_THRESHOLD_G = 1.10f

    const val BAND_GREEN_MAX_G = 1.30f
    const val BAND_YELLOW_MAX_G = 1.70f
    const val BAND_ORANGE_MAX_G = 2.20f

    fun ms2ToG(ms2: Float): Float = ms2 / STANDARD_GRAVITY_MS2

    fun gToMs2(g: Float): Float = g * STANDARD_GRAVITY_MS2

    fun recordingThresholdMs2(): Float = gToMs2(RECORDING_THRESHOLD_G)

    fun isRecordableG(shockG: Float): Boolean = shockG >= RECORDING_THRESHOLD_G

    /**
     * Normalize a profile / UI threshold that may be stored as g or m/s² into g.
     */
    fun asThresholdG(raw: Float): Float {
        if (raw <= 0f) return RECORDING_THRESHOLD_G
        return if (raw > 4f) ms2ToG(raw) else raw
    }
}
