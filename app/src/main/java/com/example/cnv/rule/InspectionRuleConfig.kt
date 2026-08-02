package com.example.cnv.rule

/**
 * Thresholds for [InspectionRuleEngine] (STEP 17-1).
 * Rules run on Analysis Result metrics — never on raw Inspection Events.
 */
data class InspectionRuleConfig(
    val highShockStrength: Float = DEFAULT_HIGH_SHOCK,
    val highShockCount: Int = DEFAULT_HIGH_SHOCK_COUNT,
    val lowCoverage: Float = DEFAULT_LOW_COVERAGE,
    val trackingLossMin: Int = DEFAULT_TRACKING_LOSS_MIN,
    val speedMismatchMmPerSec: Float = DEFAULT_SPEED_MISMATCH,
    val validationWarningScore: Float = DEFAULT_VALIDATION_WARN,
    val zoneLowCoverage: Float = DEFAULT_ZONE_LOW_COVERAGE,
    val zoneHighShockCount: Int = DEFAULT_ZONE_HIGH_SHOCK,
) {
    companion object {
        const val DEFAULT_HIGH_SHOCK = 2.5f
        const val DEFAULT_HIGH_SHOCK_COUNT = 5
        const val DEFAULT_LOW_COVERAGE = 0.5f
        const val DEFAULT_TRACKING_LOSS_MIN = 1
        const val DEFAULT_SPEED_MISMATCH = 50f
        const val DEFAULT_VALIDATION_WARN = 0.7f
        const val DEFAULT_ZONE_LOW_COVERAGE = 0.3f
        const val DEFAULT_ZONE_HIGH_SHOCK = 2

        val DEFAULT = InspectionRuleConfig()
    }
}
