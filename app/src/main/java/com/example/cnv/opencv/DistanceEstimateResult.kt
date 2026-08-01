package com.example.cnv.opencv

/**
 * Per-frame distance estimation output for UI / fusion consumers.
 */
data class DistanceEstimateResult(
    val pixelDistance: Float,
    val medianPixel: Float,
    val distanceMm: Float,
    val accumulatedMm: Float,
    val trackingFeatureCount: Int,
    val inlierCount: Int,
    val outlierCount: Int,
    val confidence: Float,
    val appliedToAccumulation: Boolean,
)
