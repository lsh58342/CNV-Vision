package com.example.cnv.vio

/**
 * Tunables for Camera + IMU visual-inertial tracking (T Phone 3 / CNV tray).
 * No ARCore. Scale from route/nominal speed prior — no walk-calibration mmPerPixel.
 */
data class VisualInertialConfig(
    /** CNV nominal: 7.98 m/min = 133 mm/s. */
    val nominalSpeedMmPerSec: Float = DEFAULT_NOMINAL_SPEED_MM_PER_SEC,
    val gyroWeight: Float = DEFAULT_GYRO_WEIGHT,
    val visualHeadingWeight: Float = DEFAULT_VISUAL_HEADING_WEIGHT,
    val turnRateDegPerSec: Float = DEFAULT_TURN_RATE_DEG_PER_SEC,
    val turnAdvanceScale: Float = DEFAULT_TURN_ADVANCE_SCALE,
    val minFeaturesGood: Int = DEFAULT_MIN_FEATURES_GOOD,
    val minFeaturesWarning: Int = DEFAULT_MIN_FEATURES_WARNING,
    val minInlierRatioGood: Float = DEFAULT_MIN_INLIER_RATIO_GOOD,
    val maxRouteDistanceMmGood: Float = DEFAULT_MAX_ROUTE_DISTANCE_MM_GOOD,
    val maxHeadingErrorDegGood: Float = DEFAULT_MAX_HEADING_ERROR_DEG_GOOD,
    val cornerProgressThreshold: Float = DEFAULT_CORNER_PROGRESS_THRESHOLD,
    val cornerHeadingErrorCurrentDeg: Float = DEFAULT_CORNER_HEADING_ERROR_CURRENT_DEG,
    val cornerHeadingErrorNextDeg: Float = DEFAULT_CORNER_HEADING_ERROR_NEXT_DEG,
    val lostFrameLimit: Int = DEFAULT_LOST_FRAME_LIMIT,
    val noiseFloorPx: Float = DEFAULT_NOISE_FLOOR_PX,
    val maxDtSec: Double = DEFAULT_MAX_DT_SEC,
) {
    companion object {
        const val DEFAULT_NOMINAL_SPEED_MM_PER_SEC = 133f
        const val DEFAULT_GYRO_WEIGHT = 0.92f
        const val DEFAULT_VISUAL_HEADING_WEIGHT = 0.08f
        const val DEFAULT_TURN_RATE_DEG_PER_SEC = 25f
        const val DEFAULT_TURN_ADVANCE_SCALE = 0.35f
        const val DEFAULT_MIN_FEATURES_GOOD = 20
        const val DEFAULT_MIN_FEATURES_WARNING = 8
        const val DEFAULT_MIN_INLIER_RATIO_GOOD = 0.40f
        const val DEFAULT_MAX_ROUTE_DISTANCE_MM_GOOD = 150f
        const val DEFAULT_MAX_HEADING_ERROR_DEG_GOOD = 35f
        const val DEFAULT_CORNER_PROGRESS_THRESHOLD = 0.82f
        const val DEFAULT_CORNER_HEADING_ERROR_CURRENT_DEG = 40f
        const val DEFAULT_CORNER_HEADING_ERROR_NEXT_DEG = 30f
        const val DEFAULT_LOST_FRAME_LIMIT = 12
        const val DEFAULT_NOISE_FLOOR_PX = 0.35f
        const val DEFAULT_MAX_DT_SEC = 0.25

        val DEFAULT: VisualInertialConfig = VisualInertialConfig()
    }
}
