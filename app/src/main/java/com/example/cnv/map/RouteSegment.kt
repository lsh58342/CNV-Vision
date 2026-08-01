package com.example.cnv.map

/**
 * Conveyor span between two nodes. Length is in millimeters.
 */
data class RouteSegment(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val lengthMm: Float,
) {
    init {
        require(lengthMm > 0f) { "Segment length must be positive: $id" }
    }
}
