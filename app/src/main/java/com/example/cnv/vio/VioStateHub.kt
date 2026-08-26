package com.example.cnv.vio

import com.example.cnv.position.TrackingPose
import com.example.cnv.position.TrackingQuality

/**
 * Process-wide VIO snapshot for MapMatching, Inspection HUD, Shock enrichment, logs.
 */
object VioStateHub {

    @Volatile
    var quality: TrackingQuality = TrackingQuality.LOST
        private set

    @Volatile
    var deviceHeadingDeg: Float = 0f
        private set

    @Volatile
    var angularVelocityDegPerSec: Float = 0f
        private set

    @Volatile
    var cumulativeRotationDeg: Float = 0f
        private set

    @Volatile
    var gyroAvailable: Boolean = false
        private set

    @Volatile
    var accelAvailable: Boolean = false
        private set

    @Volatile
    var rotationVectorAvailable: Boolean = false
        private set

    @Volatile
    var imuHz: Float = 0f
        private set

    @Volatile
    var featureCount: Int = 0
        private set

    @Volatile
    var trackedFeatureCount: Int = 0
        private set

    @Volatile
    var inlierCount: Int = 0
        private set

    @Volatile
    var inlierRatio: Float = 0f
        private set

    @Volatile
    var opticalFlowErrorPx: Float = 0f
        private set

    @Volatile
    var cameraFps: Float = 0f
        private set

    @Volatile
    var routeProgressMm: Float = 0f
        private set

    @Volatile
    var distanceToRouteMm: Float = 0f
        private set

    @Volatile
    var segmentId: String = ""
        private set

    @Volatile
    var routeHeadingDeg: Float = 0f
        private set

    @Volatile
    var headingErrorDeg: Float = 0f
        private set

    @Volatile
    var projectedX: Double = 0.0
        private set

    @Volatile
    var projectedY: Double = 0.0
        private set

    @Volatile
    var mount: CameraMountCalibration = CameraMountCalibration.IDENTITY
        private set

    @Volatile
    var latestPose: TrackingPose? = null
        private set

    fun setMount(calibration: CameraMountCalibration) {
        mount = calibration
    }

    fun onImuCapabilities(
        gyro: Boolean,
        accel: Boolean,
        rotationVector: Boolean,
    ) {
        gyroAvailable = gyro
        accelAvailable = accel
        rotationVectorAvailable = rotationVector
        println(
            "LOG[VIO][IMU] caps gyro=$gyro accel=$accel rotationVector=$rotationVector",
        )
    }

    fun onImuSample(
        headingDeg: Float,
        angularVelocityDegPerSec: Float,
        cumulativeRotationDeg: Float,
        imuHz: Float,
    ) {
        this.deviceHeadingDeg = headingDeg
        this.angularVelocityDegPerSec = angularVelocityDegPerSec
        this.cumulativeRotationDeg = cumulativeRotationDeg
        this.imuHz = imuHz
    }

    fun onPose(pose: TrackingPose) {
        latestPose = pose
        quality = pose.quality
        deviceHeadingDeg = pose.deviceHeadingDeg
        angularVelocityDegPerSec = pose.angularVelocityDegPerSec
        cumulativeRotationDeg = pose.cumulativeRotationDeg
        featureCount = pose.featureCount
        trackedFeatureCount = pose.trackedFeatureCount
        inlierCount = pose.inlierCount
        inlierRatio = pose.inlierRatio
        opticalFlowErrorPx = pose.opticalFlowErrorPx
        cameraFps = pose.cameraFps
    }

    fun onRouteMatch(
        segmentId: String,
        routeProgressMm: Float,
        projectedX: Double,
        projectedY: Double,
        distanceToRouteMm: Float,
        routeHeadingDeg: Float,
        headingErrorDeg: Float,
    ) {
        this.segmentId = segmentId
        this.routeProgressMm = routeProgressMm
        this.projectedX = projectedX
        this.projectedY = projectedY
        this.distanceToRouteMm = distanceToRouteMm
        this.routeHeadingDeg = routeHeadingDeg
        this.headingErrorDeg = headingErrorDeg
    }

    fun reset() {
        quality = TrackingQuality.LOST
        deviceHeadingDeg = 0f
        angularVelocityDegPerSec = 0f
        cumulativeRotationDeg = 0f
        imuHz = 0f
        featureCount = 0
        trackedFeatureCount = 0
        inlierCount = 0
        inlierRatio = 0f
        opticalFlowErrorPx = 0f
        cameraFps = 0f
        routeProgressMm = 0f
        distanceToRouteMm = 0f
        segmentId = ""
        routeHeadingDeg = 0f
        headingErrorDeg = 0f
        projectedX = 0.0
        projectedY = 0.0
        latestPose = null
    }
}
