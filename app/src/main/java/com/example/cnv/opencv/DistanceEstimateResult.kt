package com.example.cnv.opencv

/**
 * Per-frame distance estimation output for UI / fusion consumers.
 * Distances follow Median policy (PROJECT_GUIDE Distance Estimation Policy).
 */
data class DistanceEstimateResult(
    val medianPixel: Float,
    val distanceMm: Float,
    val accumulatedMm: Float,
    val trackingFeatureCount: Int,
    val confidence: Float,
    val appliedToAccumulation: Boolean,
)
