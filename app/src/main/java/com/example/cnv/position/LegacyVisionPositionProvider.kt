package com.example.cnv.position

import com.example.cnv.config.CalibrationManager
import com.example.cnv.opencv.DistanceEstimator
import com.example.cnv.opencv.OpticalFlowDistanceEstimator

/**
 * Legacy Camera optical-flow → mmPerPixel distance path (kept for A/B).
 */
class LegacyVisionPositionProvider(
    calibrationManager: CalibrationManager,
) : PositionProvider {

    private val estimator = OpticalFlowDistanceEstimator(calibrationManager)

    override fun mode(): PositionProviderMode = PositionProviderMode.LEGACY_VISION

    override fun distanceEstimator(): DistanceEstimator = estimator

    override fun reset() {
        estimator.reset()
    }

    override fun latestPose(): TrackingPose? = null

    override fun trackingQuality(): TrackingQuality = TrackingQuality.WARNING
}
