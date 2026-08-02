package com.example.cnv.rule

/**
 * Analysis Result metric keys evaluated by [InspectionRuleEngine] (STEP 18).
 * Thresholds live on [RuleDefinition] — no magic numbers in the engine.
 */
enum class RuleMetric {
    MAX_SHOCK,
    AVG_SHOCK,
    SHOCK_COUNT,
    SHOCK_DENSITY,
    TRACKING_CONFIDENCE_MIN,
    TRACKING_LOSS,
    LOW_CONFIDENCE_COUNT,
    ROUTE_COVERAGE,
    DRAWING_COVERAGE,
    INSPECTION_RATIO,
    AVG_SPEED_MM_PER_SEC,
    NOMINAL_SPEED_DIFF_MM_PER_SEC,
    SPEED_STABILITY_RANGE,
    VALIDATION_SCORE,
    ZONE_SHOCK_COUNT,
    ZONE_COVERAGE,
    ZONE_INSPECTION_TIME_MS,
    ZONE_VALIDATION_SCORE,
    SESSION_DURATION_MS,
    SESSION_DISTANCE_MM,
    SESSION_COMPLETENESS,
}

enum class RuleCompareOp {
    /** Trigger when metric >= threshold. */
    GTE,
    /** Trigger when metric <= threshold. */
    LTE,
}
