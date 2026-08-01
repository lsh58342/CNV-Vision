package com.example.cnv.opencv

import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.video.Video

/**
 * Lucas-Kanade optical flow between the previous and current grayscale frames.
 */
class LucasKanadeOpticalFlow {

    private var previousGray: Mat? = null
    private var previousPoints: MatOfPoint2f? = null

    data class TrackResult(
        val pairs: List<FlowPair>,
    )

    /**
     * Tracks previous points into [gray], then updates previous state from [currentKeypoints].
     */
    fun track(gray: Mat, currentKeypoints: Array<KeyPoint>): TrackResult {
        val pairs = mutableListOf<FlowPair>()
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
            val errorArray = error.toArray()

            val pairCount = minOf(prevArray.size, nextArray.size, statusArray.size)
            for (index in 0 until pairCount) {
                if (statusArray[index].toInt() != TRACK_STATUS_OK) {
                    continue
                }
                val err = if (index < errorArray.size) errorArray[index] else 0f
                pairs.add(
                    FlowPair(
                        from = prevArray[index],
                        to = nextArray[index],
                        error = err,
                    ),
                )
            }

            nextPoints.release()
            status.release()
            error.release()
        }

        updatePreviousFrame(gray, currentKeypoints)
        return TrackResult(pairs = pairs)
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

    companion object {
        const val WINDOW_SIZE = 21
        const val MAX_PYRAMID_LEVEL = 3
        const val TERM_CRITERIA_COUNT = 30
        const val TERM_CRITERIA_EPS = 0.01
        const val MAX_TRACK_POINTS = 200
        const val TRACK_STATUS_OK = 1
    }
}
