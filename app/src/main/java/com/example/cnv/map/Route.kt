package com.example.cnv.map

/**
 * Full conveyor route graph (nodes + segments + edges).
 * Topology only — no CAD geometry.
 */
data class Route(
    val id: String,
    val name: String,
    val nodes: List<RouteNode>,
    val segments: List<RouteSegment>,
    val edges: List<RouteEdge>,
    val startNodeId: String,
    val startSegmentId: String,
) {
    private val nodeById: Map<String, RouteNode> = nodes.associateBy { it.id }
    private val segmentById: Map<String, RouteSegment> = segments.associateBy { it.id }

    fun node(id: String): RouteNode? = nodeById[id]

    fun segment(id: String): RouteSegment? = segmentById[id]

    fun outgoingEdges(nodeId: String): List<RouteEdge> =
        edges.filter { it.fromNodeId == nodeId }

    fun preferredOutgoingEdge(nodeId: String): RouteEdge? {
        val outgoing = outgoingEdges(nodeId)
        return outgoing.firstOrNull { it.preferred } ?: outgoing.firstOrNull()
    }
}
