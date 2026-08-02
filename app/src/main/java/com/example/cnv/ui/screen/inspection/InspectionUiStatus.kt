package com.example.cnv.ui.screen.inspection

/**
 * UI status snapshot for Inspection Screen (display only — no new calculations).
 */
data class InspectionUiStatus(
    val trackingLabel: String = "IDLE",
    val distanceMm: Float = 0f,
    val shockCount: Int = 0,
    val elapsedSec: Double = 0.0,
    val sessionState: String = "IDLE",
    val running: Boolean = false,
    /** Continuous Nominal vs Measured mismatch (STEP 15-2). */
    val speedMismatchWarning: Boolean = false,
    val speedValidationConfidence: Float? = null,
    val validatedFusionConfidence: Float? = null,
)
