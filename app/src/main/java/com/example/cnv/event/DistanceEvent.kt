package com.example.cnv.event

/**
 * Published by DistanceEstimator implementations after each valid frame estimate.
 */
data class DistanceEvent(
    override val timestampNs: Long,
    val medianPixel: Float,
    val distanceMm: Float,
    val accumulatedMm: Float,
    val confidence: Float,
    val trackingFeatureCount: Int,
) : BaseEvent
