package com.example.cnv.speed

/**
 * Config for Speed Validation (STEP 15-2).
 * Nominal Speed is Reference only — never used to overwrite Distance.
 */
data class SpeedValidationConfig(
    val baseConfidence: Float = DEFAULT_BASE_CONFIDENCE,
    val withinToleranceBoost: Float = DEFAULT_WITHIN_BOOST,
    val overTolerancePenalty: Float = DEFAULT_OVER_PENALTY,
    /** difference / expected >= toleranceFraction * this → Outlier. */
    val outlierToleranceMultiplier: Float = DEFAULT_OUTLIER_MULTIPLIER,
    /** Consecutive over-tolerance frames before mismatch warning. */
    val continuousMismatchThreshold: Int = DEFAULT_CONTINUOUS_MISMATCH,
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
    val maxConfidence: Float = DEFAULT_MAX_CONFIDENCE,
    /** Avoid divide-by-zero when expected distance is tiny. */
    val minExpectedDistanceMm: Float = DEFAULT_MIN_EXPECTED_MM,
) {
    companion object {
        val DEFAULT = SpeedValidationConfig()
        const val DEFAULT_BASE_CONFIDENCE = 1.0f
        const val DEFAULT_WITHIN_BOOST = 0.05f
        const val DEFAULT_OVER_PENALTY = 0.35f
        const val DEFAULT_OUTLIER_MULTIPLIER = 3.0f
        const val DEFAULT_CONTINUOUS_MISMATCH = 10
        const val DEFAULT_MIN_CONFIDENCE = 0.05f
        const val DEFAULT_MAX_CONFIDENCE = 1.0f
        const val DEFAULT_MIN_EXPECTED_MM = 0.001f
        /** m/min → mm/s: * 1000 / 60 */
        const val M_PER_MIN_TO_MM_PER_SEC = 1000f / 60f
    }
}
