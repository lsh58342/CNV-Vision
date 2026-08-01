package com.example.cnv.route

import com.example.cnv.dwg.Point2d

/**
 * Extracted conveyor center-line candidate. Not a [com.example.cnv.map.Route].
 * Coordinate mapping / Route generation happens in STEP 10-2.
 */
data class RouteCandidate(
    val id: String,
    val layerName: String,
    val centerLine: List<Point2d>,
    val length: Double,
    val sourcePolylineCount: Int,
    val confidence: Float,
)
