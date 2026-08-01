package com.example.cnv.map

import com.example.cnv.core.model.RouteDirection

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
