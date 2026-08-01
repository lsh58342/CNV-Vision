package com.example.cnv.map

import com.example.cnv.core.event.BaseEvent

/**
 * Published by [MapMatchingEngine] after a [RoutePosition] update.
 * Consumers: CAD Viewer (STEP 11), HeatMap (STEP 12).
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
