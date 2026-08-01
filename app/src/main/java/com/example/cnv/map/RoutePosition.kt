package com.example.cnv.map

/**
 * Position on the route topology (no CAD coordinates).
 */
data class RoutePosition(
    val segmentId: String,
    val nodeId: String,
    val distanceFromSegmentStart: Float,
    val progress: Float,
    val direction: RouteDirection,
    val timestampNs: Long,
    val confidence: Float,
)
