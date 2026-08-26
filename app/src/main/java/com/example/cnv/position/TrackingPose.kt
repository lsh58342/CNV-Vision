package com.example.cnv.position

/**
 * Relative pose snapshot after Visual-Inertial fusion (before or after route projection).
 */
data class TrackingPose(
    val timestampNs: Long,
    val deviceHeadingDeg: Float,
    val angularVelocityDegPerSec: Float,
    val cumulativeRotationDeg: Float,
    val visualTxPx: Double,
    val visualTyPx: Double,
    val featureCount: Int,
    val trackedFeatureCount: Int,
    val inlierCount: Int,
    val inlierRatio: Float,
    val opticalFlowErrorPx: Float,
    val cameraFps: Float,
    val quality: TrackingQuality,
    val deltaDistanceMm: Float,
    val confidence: Float,
)
