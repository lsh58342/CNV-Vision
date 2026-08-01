package com.example.cnv.fusion

/**
 * Component scores and overall fusion confidence (0..1).
 * Computed only inside [FusionRuleEngine].
 */
data class FusionConfidence(
    val overall: Float,
    val distanceConfidence: Float,
    val shockConfidence: Float,
    val trackingScore: Float,
    val peakAccelerationScore: Float,
    val calibrationScore: Float,
    val ransacConfidence: Float,
)
