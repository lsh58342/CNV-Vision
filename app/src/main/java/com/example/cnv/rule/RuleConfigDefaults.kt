package com.example.cnv.rule

/**
 * Threshold / severity defaults used only when seeding [RuleDefinitionRepository].
 * Runtime evaluation reads definitions from the repository — no magic numbers in Engine.
 */
object RuleConfigDefaults {
    const val HIGH_SHOCK_STRENGTH = 2.5f
    const val HIGH_SHOCK_COUNT = 5
    const val HIGH_SHOCK_DENSITY = 2f
    const val TRACKING_LOSS_MIN = 1f
    const val LOW_CONFIDENCE_COUNT = 10f
    const val MIN_TRACKING_CONFIDENCE = 0.4f
    const val LOW_ROUTE_COVERAGE = 0.5f
    const val LOW_DRAWING_COVERAGE = 0.5f
    const val SPEED_MISMATCH_MM_PER_SEC = 50f
    const val SPEED_STABILITY_RANGE = 80f
    const val VALIDATION_WARN_SCORE = 0.7f
    const val ZONE_HIGH_SHOCK_COUNT = 2f
    const val ZONE_LOW_COVERAGE = 0.3f
    const val SESSION_MIN_DURATION_MS = 5_000f
    const val SESSION_MIN_DISTANCE_MM = 100f
    const val SESSION_MIN_COMPLETENESS = 0.4f
    const val ZERO_TOLERANCE = 0f
}
