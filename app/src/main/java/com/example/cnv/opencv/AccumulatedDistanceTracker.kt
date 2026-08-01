package com.example.cnv.opencv

/**
 * Session accumulated distance with quality gates.
 */
class AccumulatedDistanceTracker {

    private var accumulatedMm: Float = 0f
    private var skippedFrames: Int = 0

    fun accumulatedMm(): Float = accumulatedMm

    fun skippedFrames(): Int = skippedFrames

    fun reset() {
        accumulatedMm = 0f
        skippedFrames = 0
    }

    /**
     * @return true if [deltaMm] was added to the accumulator.
     */
    fun tryAccumulate(
        deltaMm: Float,
        inlierCount: Int,
        trackedCount: Int,
        medianPixel: Float,
        confidence: Float,
        calibrated: Boolean,
    ): Boolean {
        if (!calibrated) {
            skippedFrames++
            return false
        }
        if (inlierCount < MIN_INLIERS) {
            skippedFrames++
            return false
        }
        val inlierRatio = if (trackedCount > 0) {
            inlierCount.toFloat() / trackedCount.toFloat()
        } else {
            0f
        }
        if (inlierRatio < MIN_INLIER_RATIO) {
            skippedFrames++
            return false
        }
        if (medianPixel <= NOISE_FLOOR_PX) {
            skippedFrames++
            return false
        }
        if (confidence < MIN_CONFIDENCE) {
            skippedFrames++
            return false
        }

        accumulatedMm += deltaMm
        return true
    }

    companion object {
        const val MIN_INLIERS = 8
        const val MIN_INLIER_RATIO = 0.35f
        const val NOISE_FLOOR_PX = 0.5f
        const val MIN_CONFIDENCE = 0.15f
    }
}
