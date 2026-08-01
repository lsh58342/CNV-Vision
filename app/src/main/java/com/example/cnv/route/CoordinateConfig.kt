package com.example.cnv.route

/**
 * World ↔ screen mapping parameters. All magic numbers live here.
 */
data class CoordinateConfig(
    val coordinateScale: Double = DEFAULT_COORDINATE_SCALE,
    val offsetX: Double = DEFAULT_OFFSET_X,
    val offsetY: Double = DEFAULT_OFFSET_Y,
    val screenScale: Double = DEFAULT_SCREEN_SCALE,
    val screenOffsetX: Double = DEFAULT_SCREEN_OFFSET_X,
    val screenOffsetY: Double = DEFAULT_SCREEN_OFFSET_Y,
    val debugHudRefreshIntervalMs: Long = DEFAULT_DEBUG_HUD_REFRESH_MS,
) {
    companion object {
        /** DWG unit → world mm (1.0 keeps DWG units as world). */
        const val DEFAULT_COORDINATE_SCALE = 1.0
        const val DEFAULT_OFFSET_X = 0.0
        const val DEFAULT_OFFSET_Y = 0.0
        const val DEFAULT_SCREEN_SCALE = 0.05
        const val DEFAULT_SCREEN_OFFSET_X = 0.0
        const val DEFAULT_SCREEN_OFFSET_Y = 0.0
        const val DEFAULT_DEBUG_HUD_REFRESH_MS = 500L

        val DEFAULT: CoordinateConfig = CoordinateConfig()
    }
}
