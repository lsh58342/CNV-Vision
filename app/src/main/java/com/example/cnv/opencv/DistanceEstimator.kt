package com.example.cnv.opencv

/**
 * Pluggable distance algorithm. Implementations may swap later (e.g. fusion-backed).
 */
interface DistanceEstimator {

    /**
     * Estimates motion for the current grayscale frame.
     *
     * @param frameTimestampNs Camera [androidx.camera.core.ImageInfo.getTimestamp] (elapsed-realtime ns).
     * @return overlay Mat (RGBA) with flow + debug HUD. Caller must [org.opencv.core.Mat.release].
     */
    fun estimate(
        gray: org.opencv.core.Mat,
        currentKeypoints: Array<org.opencv.core.KeyPoint>,
        frameTimestampNs: Long,
    ): Pair<org.opencv.core.Mat, DistanceEstimateResult>

    fun reset()
}
