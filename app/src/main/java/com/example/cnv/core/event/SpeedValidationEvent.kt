package com.example.cnv.core.event

/**
 * Published by SpeedValidatorEngine after validating Measured vs Expected Distance (STEP 15-2).
 * Reference validation only — consumers must not overwrite Distance.
 */
data class SpeedValidationEvent(
    override val timestampNs: Long,
    val nominalSpeedMPerMin: Float,
    val expectedDistanceMm: Float,
    val measuredDistanceMm: Float,
    val differenceMm: Float,
    val confidence: Float,
    val outlier: Boolean,
    val withinTolerance: Boolean,
    val speedMismatchWarning: Boolean,
    val validatedFusionConfidence: Float,
) : BaseEvent
