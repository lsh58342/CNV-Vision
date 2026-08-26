package com.example.cnv.position

import com.example.cnv.opencv.DistanceEstimator

/**
 * Pluggable position / distance source for Inspection.
 *
 * Default: [VisualInertialPositionProvider]
 * Legacy: [LegacyVisionPositionProvider] (optical-flow mmPerPixel path — optional)
 * Default: [VisualInertialPositionProvider] (route/nominal speed, no walk calibration)
 */
interface PositionProvider {
    fun mode(): PositionProviderMode

    /** OpenCV frame estimator that publishes DistanceEvent. */
    fun distanceEstimator(): DistanceEstimator

    fun reset()

    fun latestPose(): TrackingPose?

    fun trackingQuality(): TrackingQuality
}

enum class PositionProviderMode {
    VISUAL_INERTIAL,
    LEGACY_VISION,
}
