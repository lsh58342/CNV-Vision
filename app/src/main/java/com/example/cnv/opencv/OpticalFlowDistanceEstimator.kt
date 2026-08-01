package com.example.cnv.opencv

import com.example.cnv.config.CalibrationManager
import com.example.cnv.core.debug.PipelinePerfMonitor
import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.DistanceEvent
import com.example.cnv.core.event.EventDispatcher
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Camera + OpenCV distance pipeline:
 * ORB → LK → RANSAC → Median → CalibrationManager.getMmPerPixel() → Δd → Accumulated.
 * Publishes [DistanceEvent] only — never references IMU.
 * Timestamps use Camera ImageInfo time base (elapsed-realtime ns).
 */
class OpticalFlowDistanceEstimator(
    private val calibrationManager: CalibrationManager,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
) : DistanceEstimator {

    private val opticalFlow = LucasKanadeOpticalFlow()
    private val ransacFlowFilter = RansacFlowFilter()
    private val pixelMovementEstimator = PixelMovementEstimator()
    private val frameDistanceEstimator = FrameDistanceEstimator(calibrationManager)
    private val accumulatedDistanceTracker = AccumulatedDistanceTracker()

    override fun estimate(
        gray: Mat,
        currentKeypoints: Array<KeyPoint>,
        frameTimestampNs: Long,
    ): Pair<Mat, DistanceEstimateResult> {
        val overlay = Mat()
        Imgproc.cvtColor(gray, overlay, Imgproc.COLOR_GRAY2RGBA)

        // TODO: Align LK coordinates with display rotation when mount orientation is not fixed.
        // Currently assumes the phone is always mounted in the same orientation (sensor frame).
        val trackResult = opticalFlow.track(gray, currentKeypoints)
        val trackedPairs = trackResult.pairs

        if (trackedPairs.isEmpty()) {
            drawKeypoints(overlay, currentKeypoints)
            val empty = DistanceEstimateResult(
                medianPixel = 0f,
                distanceMm = 0f,
                accumulatedMm = accumulatedDistanceTracker.accumulatedMm(),
                trackingFeatureCount = 0,
                confidence = 0f,
                appliedToAccumulation = false,
            )
            drawDebugHud(overlay, empty)
            publishDistanceEvent(empty, frameTimestampNs)
            return overlay to empty
        }

        val filterResult = ransacFlowFilter.filter(trackedPairs)
        drawFlow(overlay, filterResult.inliers, INLIER_COLOR)
        drawFlow(overlay, filterResult.outliers, OUTLIER_COLOR)

        val medianPixel = pixelMovementEstimator.medianMagnitude(filterResult.inliers)
        calibrationManager.addObservedPixelDistance(medianPixel)

        val distanceMm = frameDistanceEstimator.toMm(medianPixel)
        val applied = accumulatedDistanceTracker.tryAccumulate(
            deltaMm = distanceMm,
            inlierCount = filterResult.inliers.size,
            trackedCount = trackedPairs.size,
            medianPixel = medianPixel,
            confidence = filterResult.confidence,
            calibrated = calibrationManager.isCalibrated(),
        )

        val result = DistanceEstimateResult(
            medianPixel = medianPixel,
            distanceMm = distanceMm,
            accumulatedMm = accumulatedDistanceTracker.accumulatedMm(),
            trackingFeatureCount = trackedPairs.size,
            confidence = filterResult.confidence,
            appliedToAccumulation = applied,
        )
        drawDebugHud(overlay, result)
        publishDistanceEvent(result, frameTimestampNs)
        return overlay to result
    }

    private fun publishDistanceEvent(result: DistanceEstimateResult, frameTimestampNs: Long) {
        eventDispatcher.dispatch(
            DistanceEvent(
                timestampNs = frameTimestampNs,
                medianPixel = result.medianPixel,
                distanceMm = result.distanceMm,
                accumulatedMm = result.accumulatedMm,
                confidence = result.confidence,
                trackingFeatureCount = result.trackingFeatureCount,
            ),
        )
        // Capture publish instant for latency (still TimeBase domain).
        PipelinePerfMonitor.markDistancePublished(frameTimestampNs)
    }

    override fun reset() {
        opticalFlow.release()
        accumulatedDistanceTracker.reset()
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

    private fun drawDebugHud(overlay: Mat, result: DistanceEstimateResult) {
        val lines = listOf(
            "Median Pixel Distance: %.2f px".format(result.medianPixel),
            "Median Distance: %.2f mm".format(result.distanceMm),
            "Tracking Feature Count: %d".format(result.trackingFeatureCount),
            "Confidence: %.2f".format(result.confidence),
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
        const val TEXT_SCALE = 0.9
        const val TEXT_THICKNESS = 2

        private val INLIER_COLOR = Scalar(0.0, 255.0, 0.0, 255.0)
        private val OUTLIER_COLOR = Scalar(255.0, 64.0, 64.0, 255.0)
        private val TEXT_COLOR = Scalar(255.0, 255.0, 0.0, 255.0)
    }
}
