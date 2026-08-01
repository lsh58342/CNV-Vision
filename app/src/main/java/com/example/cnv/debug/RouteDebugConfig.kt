package com.example.cnv.debug

/**
 * Route debug viewer drawing / interaction defaults.
 */
data class RouteDebugConfig(
    val nodeRadiusPx: Float = DEFAULT_NODE_RADIUS_PX,
    val segmentWidthPx: Float = DEFAULT_SEGMENT_WIDTH_PX,
    val minZoom: Float = DEFAULT_MIN_ZOOM,
    val maxZoom: Float = DEFAULT_MAX_ZOOM,
    val currentPositionRadiusPx: Float = DEFAULT_CURRENT_POSITION_RADIUS_PX,
    val statsRefreshIntervalMs: Long = DEFAULT_STATS_REFRESH_MS,
) {
    companion object {
        const val DEFAULT_NODE_RADIUS_PX = 8f
        const val DEFAULT_SEGMENT_WIDTH_PX = 4f
        const val DEFAULT_MIN_ZOOM = 0.25f
        const val DEFAULT_MAX_ZOOM = 8f
        const val DEFAULT_CURRENT_POSITION_RADIUS_PX = 10f
        const val DEFAULT_STATS_REFRESH_MS = 300L

        val DEFAULT: RouteDebugConfig = RouteDebugConfig()
    }
}
