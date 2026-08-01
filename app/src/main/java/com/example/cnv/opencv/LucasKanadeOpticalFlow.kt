package com.example.cnv.opencv

import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.core.KeyPoint
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.sqrt

/**
 * Lucas-Kanade optical flow between the previous and current grayscale frames.
 * Reports mean tracked displacement in pixels for the current frame pair only.
 */
class LucasKanadeOpticalFlow {

    private var previousGray: Mat? = null
    private var previousPoints: MatOfPoint2f? = null

    data class Result(
        val overlay: Mat,
        val movementDistancePx: Float,
    )

    /**
     * @param gray current grayscale frame (not retained; cloned internally as needed)
     * @param currentKeypoints ORB keypoints on [gray] used as next prev points
     * @return RGBA overlay with flow vectors. Caller must [Mat.release] [Result.overlay].
     */
    fun process(gray: Mat, currentKeypoints: Array<KeyPoint>): Result {
        val overlay = Mat()
        Imgproc.cvtColor(gray, overlay, Imgproc.COLOR_GRAY2RGBA)

        var movementDistancePx = 0f
        val prevGray = previousGray
        val prevPoints = previousPoints

        if (prevGray != null && prevPoints != null && !prevPoints.empty()) {
            val nextPoints = MatOfPoint2f()
            val status = MatOfByte()
            val error = MatOfFloat()

            Video.calcOpticalFlowPyrLK(
                prevGray,
                gray,
                prevPoints,
                nextPoints,
                status,
                error,
                Size(WINDOW_SIZE.toDouble(), WINDOW_SIZE.toDouble()),
                MAX_PYRAMID_LEVEL,
                TermCriteria(
                    TermCriteria.COUNT + TermCriteria.EPS,
                    TERM_CRITERIA_COUNT,
                    TERM_CRITERIA_EPS,
                ),
            )

            val prevArray = prevPoints.toArray()
            val nextArray = nextPoints.toArray()
            val statusArray = status.toArray()

            var displacementSum = 0.0
            var trackedCount = 0

            val pairCount = minOf(prevArray.size, nextArray.size, statusArray.size)
            for (index in 0 until pairCount) {
                if (statusArray[index].toInt() != TRACK_STATUS_OK) {
                    continue
                }
                val from = prevArray[index]
                val to = nextArray[index]
                Imgproc.line(overlay, from, to, FLOW_LINE_COLOR, FLOW_LINE_THICKNESS)
                Imgproc.circle(
                    overlay,
                    to,
                    FLOW_POINT_RADIUS_PX,
                    FLOW_POINT_COLOR,
                    FLOW_POINT_THICKNESS,
                )
                val dx = to.x - from.x
                val dy = to.y - from.y
                displacementSum += sqrt(dx * dx + dy * dy)
                trackedCount++
            }

            if (trackedCount > 0) {
                movementDistancePx = (displacementSum / trackedCount).toFloat()
            }

            drawMovementText(overlay, movementDistancePx)

            nextPoints.release()
            status.release()
            error.release()
        } else {
            drawKeypoints(overlay, currentKeypoints)
        }

        updatePreviousFrame(gray, currentKeypoints)
        return Result(overlay = overlay, movementDistancePx = movementDistancePx)
    }

    fun release() {
        previousGray?.release()
        previousGray = null
        previousPoints?.release()
        previousPoints = null
    }

    private fun updatePreviousFrame(gray: Mat, currentKeypoints: Array<KeyPoint>) {
        previousGray?.release()
        previousGray = Mat()
        gray.copyTo(previousGray)

        previousPoints?.release()
        val points = currentKeypoints
            .take(MAX_TRACK_POINTS)
            .map { Point(it.pt.x, it.pt.y) }
            .toTypedArray()
        previousPoints = if (points.isEmpty()) {
            MatOfPoint2f()
        } else {
            MatOfPoint2f(*points)
        }
    }

    private fun drawKeypoints(overlay: Mat, keypoints: Array<KeyPoint>) {
        for (keypoint in keypoints) {
            Imgproc.circle(
                overlay,
                Point(keypoint.pt.x, keypoint.pt.y),
                FLOW_POINT_RADIUS_PX,
                FLOW_POINT_COLOR,
                FLOW_POINT_THICKNESS,
            )
        }
    }

    private fun drawMovementText(overlay: Mat, movementDistancePx: Float) {
        Imgproc.putText(
            overlay,
            "move: %.1f px".format(movementDistancePx),
            Point(TEXT_ORIGIN_X, TEXT_ORIGIN_Y),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            TEXT_SCALE,
            TEXT_COLOR,
            TEXT_THICKNESS,
        )
    }

    companion object {
        const val WINDOW_SIZE = 21
        const val MAX_PYRAMID_LEVEL = 3
        const val TERM_CRITERIA_COUNT = 30
        const val TERM_CRITERIA_EPS = 0.01
        const val MAX_TRACK_POINTS = 200
        const val TRACK_STATUS_OK = 1
        const val FLOW_LINE_THICKNESS = 2
        const val FLOW_POINT_RADIUS_PX = 3
        const val FLOW_POINT_THICKNESS = -1
        const val TEXT_ORIGIN_X = 24.0
        const val TEXT_ORIGIN_Y = 48.0
        const val TEXT_SCALE = 1.0
        const val TEXT_THICKNESS = 2

        private val FLOW_LINE_COLOR = Scalar(0.0, 255.0, 255.0, 255.0)
        private val FLOW_POINT_COLOR = Scalar(0.0, 255.0, 0.0, 255.0)
        private val TEXT_COLOR = Scalar(255.0, 255.0, 0.0, 255.0)
    }
}
