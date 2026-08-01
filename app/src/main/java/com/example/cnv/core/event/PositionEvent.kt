package com.example.cnv.core.event

import com.example.cnv.core.model.RouteDirection

/**
 * Published by MapMatchingEngine after a RoutePosition update.
 * Consumers: Inspection, CAD Viewer (future), HeatMap (future).
 * Does not carry CAD coordinates.
 */
data class PositionEvent(
    override val timestampNs: Long,
    val segmentId: String,
    val nodeId: String,
    val distanceFromSegmentStart: Float,
    val progress: Float,
    val direction: RouteDirection,
    val confidence: Float,
) : BaseEvent
