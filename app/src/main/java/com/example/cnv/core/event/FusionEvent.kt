package com.example.cnv.core.event

/**
 * Published by FusionEngine after rule-based Distance+Shock fusion.
 * Consumers: Map Matching (STEP 10), CAD (STEP 11), HeatMap (STEP 12).
 *
 * Does not carry Position.
 */
data class FusionEvent(
    override val timestampNs: Long,
    val distanceMm: Float,
    val confidence: Float,
    val shockLevel: Float,
    val trackingCount: Int,
    val peakAcceleration: Float,
    val eventType: FusionEventType,
    val timestampDelayNs: Long,
    val distanceConfidence: Float,
    val shockConfidence: Float,
    val calibrated: Boolean,
) : BaseEvent
