package com.example.cnv.route

import com.example.cnv.map.Route

/**
 * Result of RouteCandidate → Route generation + coordinate mapping setup.
 */
data class RouteImportResult(
    val route: Route,
    val routeName: String,
    val nodeCount: Int,
    val segmentCount: Int,
    val branchCount: Int,
    val totalRouteLengthMm: Float,
    val coordinateScale: Double,
    val coordinateOffsetX: Double,
    val coordinateOffsetY: Double,
    val mapper: CoordinateMapper,
)
