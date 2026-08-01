package com.example.cnv.opencv

import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Camera + OpenCV distance pipeline:
 * ORB seed → LK → RANSAC → Median → Calibration → Δd → Accumulated.
 */
class OpticalFlowDistanceEstimator(
    private val calibrator: DistanceCalibrator = DistanceCalibrator(),
) : DistanceEstimator {

    private val opticalFlow = LucasKanadeOpticalFlow()
    private val ransacFlowFilter = RansacFlowFilter()
    private val pixelMovementEstimator = PixelMovementEstimator()
    private val frameDistanceEstimator = FrameDistanceEstimator(calibrator)
    private val accumulatedDistanceTracker = AccumulatedDistanceTracker()

    override fun estimate(
        gray: Mat,
        currentKeypoints: Array<KeyPoint>,
    ): Pair<Mat, DistanceEstimateResult> {
        val overlay = Mat()
        Imgproc.cvtColor(gray, overlay, Imgproc.COLOR_GRAY2RGBA)

        val trackResult = opticalFlow.track(gray, currentKeypoints)
        val trackedPairs = trackResult.pairs

        if (trackedPairs.isEmpty()) {
            drawKeypoints(overlay, currentKeypoints)
            val empty = DistanceEstimateResult(
                pixelDistance = 0f,
                medianPixel = 0f,
                distanceMm = 0f,
                accumulatedMm = accumulatedDistanceTracker.accumulatedMm(),
                trackingFeatureCount = 0,
                inlierCount = 0,
                outlierCount = 0,
                confidence = 0f,
                appliedToAccumulation = false,
            )
            drawDebugHud(overlay, empty)
            return overlay to empty
        }

        val filterResult = ransacFlowFilter.filter(trackedPairs)
        drawFlow(overlay, filterResult.inliers, INLIER_COLOR)
        drawFlow(overlay, filterResult.outliers, OUTLIER_COLOR)

        val medianPixel = pixelMovementEstimator.medianMagnitude(filterResult.inliers)
        val pixelDistance = pixelMovementEstimator.meanMagnitude(filterResult.inliers)
        val consensusPixel = sqrt(
            filterResult.consensusTx * filterResult.consensusTx +
                filterResult.consensusTy * filterResult.consensusTy,
        ).toFloat()
        // Pixel Distance: consensus translation magnitude (fallback to mean if zero)
        val displayPixelDistance = if (consensusPixel > 0f) consensusPixel else pixelDistance

        val distanceMm = frameDistanceEstimator.toMm(medianPixel)
        val applied = accumulatedDistanceTracker.tryAccumulate(
            deltaMm = distanceMm,
            inlierCount = filterResult.inliers.size,
            trackedCount = trackedPairs.size,
            medianPixel = medianPixel,
            confidence = filterResult.confidence,
            calibrated = calibrator.isCalibrated(),
        )

        val result = DistanceEstimateResult(
            pixelDistance = displayPixelDistance,
            medianPixel = medianPixel,
            distanceMm = distanceMm,
            accumulatedMm = accumulatedDistanceTracker.accumulatedMm(),
            trackingFeatureCount = trackedPairs.size,
            inlierCount = filterResult.inliers.size,
            outlierCount = filterResult.outliers.size,
            confidence = filterResult.confidence,
            appliedToAccumulation = applied,
        )
        drawDebugHud(overlay, result)
        return overlay to result
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
            "Pixel Distance: %.2f px".format(result.pixelDistance),
            "Median Pixel: %.2f px".format(result.medianPixel),
            "mm Distance: %.2f mm".format(result.distanceMm),
            "Accumulated Distance: %.2f mm".format(result.accumulatedMm),
            "Tracking Feature Count: %d".format(result.trackingFeatureCount),
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
