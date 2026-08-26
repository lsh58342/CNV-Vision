package com.example.cnv.vio

import com.example.cnv.core.debug.PipelinePerfMonitor
import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.DistanceEvent
import com.example.cnv.core.event.EventDispatcher
import com.example.cnv.opencv.AccumulatedDistanceTracker
import com.example.cnv.opencv.DistanceEstimateResult
import com.example.cnv.opencv.DistanceEstimator
import com.example.cnv.opencv.FlowPair
import com.example.cnv.opencv.LucasKanadeOpticalFlow
import com.example.cnv.opencv.OpticalFlowDebugHub
import com.example.cnv.opencv.PixelMovementEstimator
import com.example.cnv.opencv.RansacFlowFilter
import com.example.cnv.position.TrackingPose
import com.example.cnv.position.TrackingQuality
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max

/**
 * Camera (ORB/LK/RANSAC) + IMU heading fusion → signed Δdistance along CNV prior.
 *
 * Scale policy (no mmPerPixel walk-calibration):
 * - Route/nominal speed prior is primary (133 mm/s default)
 * - Optical flow + IMU decide motion / turn / confidence only
 * - Accelerometer is never integrated for distance
 */
class VisualInertialDistanceEstimator(
    private val fusion: VisualInertialFusion,
    private val config: VisualInertialConfig = VisualInertialConfig.DEFAULT,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
) : DistanceEstimator {

    private val opticalFlow = LucasKanadeOpticalFlow()
    private val ransacFlowFilter = RansacFlowFilter()
    private val pixelMovementEstimator = PixelMovementEstimator()
    private val accumulatedDistanceTracker = AccumulatedDistanceTracker()

    private var lastFrameNs: Long = 0L
    private var consecutiveLost: Int = 0
    private var fpsFrameCount: Int = 0
    private var logTick: Int = 0
    private var fpsWindowStartNs: Long = 0L
    private var fps: Float = 0f
    private var sessionAccumulatedMm: Float = 0f

    @Volatile
    var latestPose: TrackingPose? = null
        private set

    @Volatile
    var latestQuality: TrackingQuality = TrackingQuality.LOST
        private set

    override fun estimate(
        gray: Mat,
        currentKeypoints: Array<KeyPoint>,
        frameTimestampNs: Long,
    ): Pair<Mat, DistanceEstimateResult> {
        val overlay = Mat()
        Imgproc.cvtColor(gray, overlay, Imgproc.COLOR_GRAY2RGBA)
        updateFps(frameTimestampNs)

        val trackResult = opticalFlow.track(gray, currentKeypoints)
        val trackedPairs = trackResult.pairs
        val featureCount = currentKeypoints.size

        if (trackedPairs.isEmpty()) {
            consecutiveLost++
            val quality = if (consecutiveLost >= config.lostFrameLimit) {
                TrackingQuality.LOST
            } else {
                TrackingQuality.WARNING
            }
            val empty = finishFrame(
                overlay = overlay,
                keypoints = currentKeypoints,
                medianPixel = 0f,
                distanceMm = 0f,
                tracked = 0,
                inliers = 0,
                confidence = 0f,
                applied = false,
                tx = 0.0,
                ty = 0.0,
                flowError = 0f,
                quality = quality,
                frameTimestampNs = frameTimestampNs,
                featureCount = featureCount,
            )
            return overlay to empty
        }

        val filterResult = ransacFlowFilter.filter(trackedPairs)
        drawFlow(overlay, filterResult.inliers, INLIER_COLOR)
        drawFlow(overlay, filterResult.outliers, OUTLIER_COLOR)

        val medianPixel = pixelMovementEstimator.medianMagnitude(filterResult.inliers)

        val tx = filterResult.consensusTx
        val ty = filterResult.consensusTy
        val inlierRatio = if (trackedPairs.isNotEmpty()) {
            filterResult.inliers.size.toFloat() / trackedPairs.size.toFloat()
        } else {
            0f
        }
        val flowError = VisualInertialFusion.residualFlowErrorPx(
            tx,
            ty,
            filterResult.inliers.map { it.to.x - it.from.x },
            filterResult.inliers.map { it.to.y - it.from.y },
        )

        OpticalFlowDebugHub.onFlowHeading(
            VisualInertialFusion.flowHeadingDeg(tx, ty),
            medianPixel,
        )
        fusion.onVisualMotion(tx, ty, inlierRatio, filterResult.confidence)

        val dtSec = computeDtSec(frameTimestampNs)
        val turning = abs(fusion.angularVelocityDegPerSec()) >= config.turnRateDegPerSec
        val visualMoving = filterResult.inliers.size >= AccumulatedDistanceTracker.MIN_INLIERS &&
            medianPixel > config.noiseFloorPx &&
            filterResult.confidence >= AccumulatedDistanceTracker.MIN_CONFIDENCE

        val quality = classifyQuality(
            tracked = trackedPairs.size,
            inlierRatio = inlierRatio,
            visualMoving = visualMoving,
        )
        if (quality == TrackingQuality.LOST) {
            consecutiveLost++
        } else {
            consecutiveLost = 0
        }

        // LOST: do not invent travel distance.
        val distanceMm = if (quality == TrackingQuality.LOST) {
            0f
        } else {
            computeDeltaMm(
                dtSec = dtSec,
                visualMoving = visualMoving,
                turning = turning,
                confidence = filterResult.confidence,
            )
        }

        val publishMm = when {
            quality == TrackingQuality.LOST -> 0f
            distanceMm > 0f && (visualMoving || turning) -> distanceMm
            else -> 0f
        }
        val applied = publishMm > 0f
        if (applied) {
            sessionAccumulatedMm += publishMm
            accumulatedDistanceTracker.tryAccumulate(
                deltaMm = publishMm,
                inlierCount = max(filterResult.inliers.size, AccumulatedDistanceTracker.MIN_INLIERS),
                trackedCount = max(trackedPairs.size, AccumulatedDistanceTracker.MIN_INLIERS),
                medianPixel = max(medianPixel, AccumulatedDistanceTracker.NOISE_FLOOR_PX + 0.01f),
                confidence = max(filterResult.confidence, AccumulatedDistanceTracker.MIN_CONFIDENCE),
                calibrated = true,
            )
        }

        val result = finishFrame(
            overlay = overlay,
            keypoints = currentKeypoints,
            medianPixel = medianPixel,
            distanceMm = publishMm,
            tracked = trackedPairs.size,
            inliers = filterResult.inliers.size,
            confidence = if (quality == TrackingQuality.LOST) {
                0f
            } else {
                filterResult.confidence.coerceAtLeast(0.3f)
            },
            applied = applied,
            tx = tx,
            ty = ty,
            flowError = flowError,
            quality = quality,
            frameTimestampNs = frameTimestampNs,
            featureCount = featureCount,
        )
        return overlay to result
    }

    private fun computeDeltaMm(
        dtSec: Double,
        visualMoving: Boolean,
        turning: Boolean,
        confidence: Float,
    ): Float {
        if (dtSec <= 0.0 || dtSec > config.maxDtSec) return 0f
        val priorMm = (config.nominalSpeedMmPerSec * dtSec).toFloat()
        if (!visualMoving && !turning) return 0f

        var advance = priorMm * confidence.coerceIn(0.35f, 1f)
        if (turning) {
            advance *= config.turnAdvanceScale
        }

        val mountSign = VioStateHub.mount.forwardSign
        return (advance * mountSign).coerceAtLeast(0f)
    }

    private fun classifyQuality(
        tracked: Int,
        inlierRatio: Float,
        visualMoving: Boolean,
    ): TrackingQuality {
        if (!VioStateHub.gyroAvailable && tracked < config.minFeaturesWarning) {
            return TrackingQuality.LOST
        }
        if (tracked < config.minFeaturesWarning) {
            return if (consecutiveLost + 1 >= config.lostFrameLimit) {
                TrackingQuality.LOST
            } else {
                TrackingQuality.WARNING
            }
        }
        if (tracked < config.minFeaturesGood || inlierRatio < config.minInlierRatioGood) {
            return TrackingQuality.WARNING
        }
        if (!visualMoving && abs(fusion.angularVelocityDegPerSec()) < 2f) {
            return TrackingQuality.WARNING
        }
        return TrackingQuality.GOOD
    }

    private fun finishFrame(
        overlay: Mat,
        keypoints: Array<KeyPoint>,
        medianPixel: Float,
        distanceMm: Float,
        tracked: Int,
        inliers: Int,
        confidence: Float,
        applied: Boolean,
        tx: Double,
        ty: Double,
        flowError: Float,
        quality: TrackingQuality,
        frameTimestampNs: Long,
        featureCount: Int,
    ): DistanceEstimateResult {
        if (tracked == 0) {
            drawKeypoints(overlay, keypoints)
        }
        val pose = TrackingPose(
            timestampNs = frameTimestampNs,
            deviceHeadingDeg = fusion.headingDeg() + VioStateHub.mount.yawOffsetDeg,
            angularVelocityDegPerSec = fusion.angularVelocityDegPerSec(),
            cumulativeRotationDeg = fusion.cumulativeRotationDeg(),
            visualTxPx = tx,
            visualTyPx = ty,
            featureCount = featureCount,
            trackedFeatureCount = tracked,
            inlierCount = inliers,
            inlierRatio = if (tracked > 0) inliers.toFloat() / tracked.toFloat() else 0f,
            opticalFlowErrorPx = flowError,
            cameraFps = fps,
            quality = quality,
            deltaDistanceMm = distanceMm,
            confidence = confidence,
        )
        latestPose = pose
        latestQuality = quality
        VioStateHub.onPose(pose)

        val published = DistanceEstimateResult(
            medianPixel = medianPixel,
            distanceMm = distanceMm,
            accumulatedMm = sessionAccumulatedMm,
            trackingFeatureCount = tracked,
            confidence = confidence,
            appliedToAccumulation = applied,
        )
        drawDebugHud(overlay, published, quality, pose)
        publishDistanceEvent(published, frameTimestampNs, quality)
        logFrame(published, pose, quality)
        return published
    }

    private fun publishDistanceEvent(
        result: DistanceEstimateResult,
        frameTimestampNs: Long,
        quality: TrackingQuality,
    ) {
        // FusionRuleEngine rejects trackingFeatureCount < 8 — pad only when tracking is usable.
        val trackingCount = when (quality) {
            TrackingQuality.LOST -> result.trackingFeatureCount
            else -> max(result.trackingFeatureCount, 8)
        }
        eventDispatcher.dispatch(
            DistanceEvent(
                timestampNs = frameTimestampNs,
                medianPixel = result.medianPixel,
                distanceMm = result.distanceMm,
                accumulatedMm = result.accumulatedMm,
                confidence = result.confidence,
                trackingFeatureCount = trackingCount,
            ),
        )
        PipelinePerfMonitor.markDistancePublished(frameTimestampNs)
    }

    private fun logFrame(
        result: DistanceEstimateResult,
        pose: TrackingPose,
        quality: TrackingQuality,
    ) {
        logTick++
        if (logTick % 8 != 0) return
        println(
            "LOG[VIO][FRAME] fps=${"%.1f".format(fps)} qual=$quality " +
                "feat=${pose.featureCount} track=${pose.trackedFeatureCount} " +
                "inlier=${pose.inlierCount} ratio=${"%.2f".format(pose.inlierRatio)} " +
                "errPx=${"%.2f".format(pose.opticalFlowErrorPx)}",
        )
        println(
            "LOG[VIO][FEATURE] count=${pose.featureCount} tracked=${pose.trackedFeatureCount} " +
                "inliers=${pose.inlierCount} ratio=${"%.2f".format(pose.inlierRatio)}",
        )
        println(
            "LOG[VIO][POSE] heading=${"%.1f".format(pose.deviceHeadingDeg)} " +
                "wz=${"%.1f".format(pose.angularVelocityDegPerSec)} " +
                "cumRot=${"%.1f".format(pose.cumulativeRotationDeg)} " +
                "dMm=${"%.2f".format(result.distanceMm)} conf=${"%.2f".format(result.confidence)}",
        )
    }

    private fun computeDtSec(frameTimestampNs: Long): Double {
        val dt = if (lastFrameNs > 0L) {
            (frameTimestampNs - lastFrameNs) * 1e-9
        } else {
            1.0 / 25.0
        }
        lastFrameNs = frameTimestampNs
        return dt.coerceIn(0.0, config.maxDtSec)
    }

    private fun updateFps(frameTimestampNs: Long) {
        if (fpsWindowStartNs == 0L) {
            fpsWindowStartNs = frameTimestampNs
            fpsFrameCount = 0
        }
        fpsFrameCount++
        val elapsed = (frameTimestampNs - fpsWindowStartNs) * 1e-9
        if (elapsed >= 1.0) {
            fps = (fpsFrameCount / elapsed).toFloat()
            fpsWindowStartNs = frameTimestampNs
            fpsFrameCount = 0
        }
    }

    override fun reset() {
        opticalFlow.release()
        accumulatedDistanceTracker.reset()
        OpticalFlowDebugHub.reset()
        fusion.reset()
        VioStateHub.reset()
        lastFrameNs = 0L
        consecutiveLost = 0
        fpsFrameCount = 0
        logTick = 0
        fpsWindowStartNs = 0L
        fps = 0f
        sessionAccumulatedMm = 0f
        latestPose = null
        latestQuality = TrackingQuality.LOST
        println("LOG[VIO] reset")
    }

    private fun drawKeypoints(overlay: Mat, keypoints: Array<KeyPoint>) {
        for (keypoint in keypoints) {
            Imgproc.circle(
                overlay,
                Point(keypoint.pt.x, keypoint.pt.y),
                POINT_RADIUS_PX,
                INLIER_COLOR,
                POINT_THICKNESS,
            )
        }
    }

    private fun drawFlow(overlay: Mat, pairs: List<FlowPair>, color: Scalar) {
        for (pair in pairs) {
            Imgproc.line(overlay, pair.from, pair.to, color, LINE_THICKNESS)
            Imgproc.circle(overlay, pair.to, POINT_RADIUS_PX, color, POINT_THICKNESS)
        }
    }

    private fun drawDebugHud(
        overlay: Mat,
        result: DistanceEstimateResult,
        quality: TrackingQuality,
        pose: TrackingPose,
    ) {
        val lines = listOf(
            "VIO $quality  FPS ${"%.1f".format(fps)}",
            "Heading ${"%.1f".format(pose.deviceHeadingDeg)}  wz ${"%.1f".format(pose.angularVelocityDegPerSec)}",
            "dMm ${"%.2f".format(result.distanceMm)}  feat ${pose.trackedFeatureCount}/${pose.inlierCount}",
            "Conf ${"%.2f".format(result.confidence)}  acc ${"%.1f".format(result.accumulatedMm)}",
        )
        var y = TEXT_ORIGIN_Y
        for (line in lines) {
            Imgproc.putText(
                overlay,
                line,
                Point(TEXT_ORIGIN_X, y),
                Imgproc.FONT_HERSHEY_SIMPLEX,
                TEXT_SCALE,
                TEXT_COLOR,
                TEXT_THICKNESS,
            )
            y += TEXT_LINE_HEIGHT
        }
    }

    companion object {
        const val POINT_RADIUS_PX = 3
        const val POINT_THICKNESS = -1
        const val LINE_THICKNESS = 2
        const val TEXT_ORIGIN_X = 24.0
        const val TEXT_ORIGIN_Y = 48.0
        const val TEXT_LINE_HEIGHT = 36.0
        const val TEXT_SCALE = 0.75
        const val TEXT_THICKNESS = 2

        private val INLIER_COLOR = Scalar(0.0, 255.0, 0.0, 255.0)
        private val OUTLIER_COLOR = Scalar(255.0, 64.0, 64.0, 255.0)
        private val TEXT_COLOR = Scalar(0.0, 255.0, 255.0, 255.0)
    }
}
