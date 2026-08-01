package com.example.cnv.route

/**
 * Route generation tolerances. All magic numbers live here.
 */
data class RouteConfig(
    val minimumSegmentLength: Double = DEFAULT_MINIMUM_SEGMENT_LENGTH,
    val maximumSegmentLength: Double = DEFAULT_MAXIMUM_SEGMENT_LENGTH,
    val snapTolerance: Double = DEFAULT_SNAP_TOLERANCE,
    val directionTolerance: Double = DEFAULT_DIRECTION_TOLERANCE,
) {
    companion object {
        const val DEFAULT_MINIMUM_SEGMENT_LENGTH = 1.0
        const val DEFAULT_MAXIMUM_SEGMENT_LENGTH = 100_000.0
        const val DEFAULT_SNAP_TOLERANCE = 5.0
        const val DEFAULT_DIRECTION_TOLERANCE = 1e-6

        val DEFAULT: RouteConfig = RouteConfig()
    }
}
