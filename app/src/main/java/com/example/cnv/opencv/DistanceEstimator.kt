package com.example.cnv.opencv

import org.opencv.core.KeyPoint
import org.opencv.core.Mat

/**
 * Pluggable distance algorithm. Implementations may swap later (e.g. fusion-backed).
 */
interface DistanceEstimator {

    /**
     * Estimates motion for the current grayscale frame.
     *
     * @return overlay Mat (RGBA) with flow + debug HUD. Caller must [Mat.release].
     */
    fun estimate(
        gray: Mat,
        currentKeypoints: Array<KeyPoint>,
    ): Pair<Mat, DistanceEstimateResult>

    fun reset()
}
