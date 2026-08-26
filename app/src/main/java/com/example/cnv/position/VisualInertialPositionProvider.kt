package com.example.cnv.position

import android.content.Context
import com.example.cnv.opencv.DistanceEstimator
import com.example.cnv.vio.CameraMountCalibration
import com.example.cnv.vio.VisualInertialConfig
import com.example.cnv.vio.VisualInertialDistanceEstimator
import com.example.cnv.vio.VisualInertialFusion
import com.example.cnv.vio.VisualInertialImuTracker
import com.example.cnv.vio.VioStateHub

/**
 * Default PositionProvider: Camera + IMU visual-inertial tracking (no ARCore).
 */
class VisualInertialPositionProvider(
    context: Context,
    private val config: VisualInertialConfig = VisualInertialConfig.DEFAULT,
    mount: CameraMountCalibration = CameraMountCalibration.IDENTITY,
) : PositionProvider {

    private val fusion = VisualInertialFusion(config)
    private val estimator = VisualInertialDistanceEstimator(
        fusion = fusion,
        config = config,
    )
    private val imuTracker = VisualInertialImuTracker(
        context = context,
        fusion = fusion,
        config = config,
    )

    init {
        VioStateHub.setMount(mount)
    }

    override fun mode(): PositionProviderMode = PositionProviderMode.VISUAL_INERTIAL

    override fun distanceEstimator(): DistanceEstimator = estimator

    fun startImu() {
        imuTracker.start()
    }

    fun stopImu() {
        imuTracker.stop()
    }

    fun setMount(mount: CameraMountCalibration) {
        VioStateHub.setMount(mount)
    }

    override fun reset() {
        estimator.reset()
    }

    override fun latestPose(): TrackingPose? = estimator.latestPose

    override fun trackingQuality(): TrackingQuality = estimator.latestQuality
}
