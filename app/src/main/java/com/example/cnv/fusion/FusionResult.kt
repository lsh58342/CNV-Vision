package com.example.cnv.fusion

import com.example.cnv.core.event.FusionEventType

/**
 * Fused observation for Map Matching / HeatMap consumers.
 *
 * Important: no Position — STEP 10 Map Matching owns spatial placement.
 */
data class FusionResult(
    val timestampNs: Long,
    val distance: Float,
    val confidence: Float,
    val shockLevel: Float,
    val trackingCount: Int,
    val peakAcceleration: Float,
    val eventType: FusionEventType,
    /** Absolute |distanceTs - shockTs|; 0 when one side missing. */
    val timestampDelayNs: Long = 0L,
    val distanceConfidence: Float = 0f,
    val shockConfidence: Float = 0f,
    val calibrated: Boolean = false,
)
