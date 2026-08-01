package com.example.cnv.map

/**
 * Directed connection from one node to another via a segment (branching support).
 */
data class RouteEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val segmentId: String,
    val preferred: Boolean = true,
)
