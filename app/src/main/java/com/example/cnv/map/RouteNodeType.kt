package com.example.cnv.map

/**
 * Semantic role of a [RouteNode] on the conveyor graph.
 * No CAD coordinates — STEP 10-3 / STEP 11 own spatial binding.
 */
enum class RouteNodeType {
    START,
    END,
    JUNCTION,
    WAYPOINT,
}
