package com.example.cnv.map

/**
 * Graph vertex: start, end, junction, or waypoint.
 * Does not include CAD / DWG coordinates.
 */
data class RouteNode(
    val id: String,
    val type: RouteNodeType,
    val label: String = id,
)
