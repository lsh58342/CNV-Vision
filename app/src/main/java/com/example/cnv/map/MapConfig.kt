package com.example.cnv.map

/**
 * Tunable map-matching parameters. All magic numbers live here.
 */
data class MapConfig(
    val matchingToleranceMm: Float = DEFAULT_MATCHING_TOLERANCE_MM,
    val maximumPositionErrorMm: Float = DEFAULT_MAXIMUM_POSITION_ERROR_MM,
    val branchToleranceMm: Float = DEFAULT_BRANCH_TOLERANCE_MM,
    val nodeRadiusMm: Float = DEFAULT_NODE_RADIUS_MM,
    val minimumConfidence: Float = DEFAULT_MINIMUM_CONFIDENCE,
    val debugHudRefreshIntervalMs: Long = DEFAULT_DEBUG_HUD_REFRESH_MS,
) {
    companion object {
        const val DEFAULT_MATCHING_TOLERANCE_MM = 50f
        const val DEFAULT_MAXIMUM_POSITION_ERROR_MM = 200f
        const val DEFAULT_BRANCH_TOLERANCE_MM = 30f
        const val DEFAULT_NODE_RADIUS_MM = 25f
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.25f
        const val DEFAULT_DEBUG_HUD_REFRESH_MS = 200L

        val DEFAULT: MapConfig = MapConfig()
    }
}
