package com.example.cnv.speed

/**
 * Per-frame Speed Validation sample (STEP 15-2).
 * Does not replace measured Distance — validation only.
 */
data class SpeedValidationSample(
    val timestampNs: Long,
    val nominalSpeedMPerMin: Float,
    val expectedDistanceMm: Float,
    val measuredDistanceMm: Float,
    val differenceMm: Float,
    val relativeError: Float,
    val toleranceFraction: Float,
    val confidence: Float,
    val outlier: Boolean,
    val withinTolerance: Boolean,
    val expectedSpeedMPerMin: Float,
    val measuredSpeedMPerMin: Float,
    val frameTimeSec: Float,
    /** Fusion confidence after Speed Validation factor (display / summary only). */
    val validatedFusionConfidence: Float? = null,
)

/**
 * Session-level Speed Validation aggregates for Inspection Summary / future AI.
 */
data class SpeedValidationSummary(
    val sampleCount: Int = 0,
    val averageExpectedSpeedMPerMin: Float = 0f,
    val averageMeasuredSpeedMPerMin: Float = 0f,
    val maximumDifferenceMm: Float = 0f,
    val averageDifferenceMm: Float = 0f,
    val validationScore: Float = 0f,
    val outlierCount: Int = 0,
    val mismatchWarningTriggered: Boolean = false,
) {
    companion object {
        val EMPTY = SpeedValidationSummary()
    }
}
