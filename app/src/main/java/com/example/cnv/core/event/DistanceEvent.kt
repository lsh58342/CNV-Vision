package com.example.cnv.core.event

/**
 * Published by DistanceEstimator after each frame estimate.
 */
data class DistanceEvent(
    override val timestampNs: Long,
    val medianPixel: Float,
    val distanceMm: Float,
    val accumulatedMm: Float,
    val confidence: Float,
    val trackingFeatureCount: Int,
) : BaseEvent
