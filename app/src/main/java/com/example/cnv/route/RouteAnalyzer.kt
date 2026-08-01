package com.example.cnv.route

import com.example.cnv.map.Route

/**
 * Computes [RouteStatistics] without modifying the route.
 */
class RouteAnalyzer {

    fun analyze(route: Route?): RouteStatistics {
        if (route == null || route.segments.isEmpty()) {
            return RouteStatistics.EMPTY.copy(nodeCount = route?.nodes?.size ?: 0)
        }
        val lengths = route.segments.map { it.lengthMm }
        val total = lengths.sum()
        val outgoing = route.nodes.associateWith { node ->
            route.outgoingEdges(node.id).size
        }
        val branchCount = outgoing.values.count { it > 1 }
        return RouteStatistics(
            totalRouteLengthMm = total,
            averageSegmentLengthMm = total / lengths.size,
            maximumSegmentLengthMm = lengths.maxOrNull() ?: 0f,
            minimumSegmentLengthMm = lengths.minOrNull() ?: 0f,
            nodeCount = route.nodes.size,
            segmentCount = route.segments.size,
            branchCount = branchCount,
        )
    }
}
