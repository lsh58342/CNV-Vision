package com.example.cnv.opencv

/**
 * Pixel movement statistics over inlier flow vectors.
 */
class PixelMovementEstimator {

    fun medianMagnitude(inliers: List<FlowPair>): Float {
        if (inliers.isEmpty()) {
            return 0f
        }
        val sorted = inliers.map { it.magnitude() }.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            ((sorted[mid - 1] + sorted[mid]) / 2.0).toFloat()
        } else {
            sorted[mid].toFloat()
        }
    }

    fun meanMagnitude(inliers: List<FlowPair>): Float {
        if (inliers.isEmpty()) {
            return 0f
        }
        return inliers.map { it.magnitude() }.average().toFloat()
    }
}
