package com.example.cnv.core.config

/**
 * OpenCV / optical-flow / ORB related defaults (values match existing feature constants).
 */
data class OpenCVConfig(
    val orbMaxFeatures: Int = DEFAULT_ORB_MAX_FEATURES,
    val lkWindowSize: Int = DEFAULT_LK_WINDOW_SIZE,
    val lkMaxPyramidLevel: Int = DEFAULT_LK_MAX_PYRAMID_LEVEL,
    val lkMaxTrackPoints: Int = DEFAULT_LK_MAX_TRACK_POINTS,
    val ransacMaxIters: Int = DEFAULT_RANSAC_MAX_ITERS,
    val ransacReprojThresholdPx: Double = DEFAULT_RANSAC_REPROJ_THRESHOLD_PX,
    val minInliers: Int = DEFAULT_MIN_INLIERS,
    val minInlierRatio: Float = DEFAULT_MIN_INLIER_RATIO,
    val noiseFloorPx: Float = DEFAULT_NOISE_FLOOR_PX,
    val minConfidence: Float = DEFAULT_MIN_CONFIDENCE,
) {
    companion object {
        const val DEFAULT_ORB_MAX_FEATURES = 500
        const val DEFAULT_LK_WINDOW_SIZE = 21
        const val DEFAULT_LK_MAX_PYRAMID_LEVEL = 3
        const val DEFAULT_LK_MAX_TRACK_POINTS = 200
        const val DEFAULT_LK_TERM_CRITERIA_COUNT = 30
        const val DEFAULT_LK_TERM_CRITERIA_EPS = 0.01
        const val DEFAULT_RANSAC_MAX_ITERS = 100
        const val DEFAULT_RANSAC_REPROJ_THRESHOLD_PX = 3.0
        const val DEFAULT_RANSAC_MIN_INLIERS_FOR_FULL_CONF = 40
        const val DEFAULT_MIN_INLIERS = 8
        const val DEFAULT_MIN_INLIER_RATIO = 0.35f
        const val DEFAULT_NOISE_FLOOR_PX = 0.5f
        const val DEFAULT_MIN_CONFIDENCE = 0.15f

        val DEFAULT: OpenCVConfig = OpenCVConfig()
    }
}
