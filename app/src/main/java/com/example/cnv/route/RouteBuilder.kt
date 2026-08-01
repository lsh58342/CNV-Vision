package com.example.cnv.route

import com.example.cnv.dwg.Point2d
import com.example.cnv.map.RouteNodeType

/**
 * Intermediate draft with topology + world anchors (not yet optimized).
 */
data class RouteDraft(
    val name: String,
    val nodes: List<DraftNode>,
    val segments: List<DraftSegment>,
    val edges: List<DraftEdge>,
) {
    data class DraftNode(
        val id: String,
        val type: RouteNodeType,
        val label: String,
        val world: WorldCoordinate,
    )

    data class DraftSegment(
        val id: String,
        val fromNodeId: String,
        val toNodeId: String,
        val lengthMm: Float,
        val start: WorldCoordinate,
        val end: WorldCoordinate,
    )

    data class DraftEdge(
        val id: String,
        val fromNodeId: String,
        val toNodeId: String,
        val segmentId: String,
        val preferred: Boolean,
    )
}

/**
 * Builds a [RouteDraft] from [RouteCandidate] center-lines (Rule Base).
 */
class RouteBuilder(
    private val routeConfig: RouteConfig = RouteConfig.DEFAULT,
    private val coordinateConfig: CoordinateConfig = CoordinateConfig.DEFAULT,
    private val transformer: CoordinateTransformer = CoordinateTransformer(coordinateConfig),
) {

    fun build(
        candidates: List<RouteCandidate>,
        routeName: String,
    ): RouteDraft {
        val nodes = mutableListOf<RouteDraft.DraftNode>()
        val segments = mutableListOf<RouteDraft.DraftSegment>()
        val edges = mutableListOf<RouteDraft.DraftEdge>()
        var nodeSeq = 0
        var segmentSeq = 0
        var edgeSeq = 0

        fun snapOrCreate(point: Point2d, preferredType: RouteNodeType): String {
            val world = transformer.pointToWorld(point)
            val existing = nodes.firstOrNull { node ->
                distance(node.world, world) <= routeConfig.snapTolerance
            }
            if (existing != null) {
                return existing.id
            }
            val id = "n-${nodeSeq++}"
            nodes.add(
                RouteDraft.DraftNode(
                    id = id,
                    type = preferredType,
                    label = id,
                    world = world,
                ),
            )
            return id
        }

        val ordered = candidates.sortedByDescending { it.length }
        for (candidate in ordered) {
            val points = candidate.centerLine
            if (points.size < 2) continue
            var previousNodeId: String? = null
            for (i in 0 until points.lastIndex) {
                val from = points[i]
                val to = points[i + 1]
                val fromWorld = transformer.pointToWorld(from)
                val toWorld = transformer.pointToWorld(to)
                val length = distance(fromWorld, toWorld)
                if (length < routeConfig.minimumSegmentLength) continue
                if (length > routeConfig.maximumSegmentLength) continue

                val fromType = when {
                    previousNodeId == null && i == 0 -> RouteNodeType.START
                    else -> RouteNodeType.WAYPOINT
                }
                val toType = if (i == points.lastIndex - 1) RouteNodeType.END else RouteNodeType.WAYPOINT
                val fromId = previousNodeId ?: snapOrCreate(from, fromType)
                val toId = snapOrCreate(to, toType)

                if (fromId == toId) continue

                val segmentId = "s-${segmentSeq++}"
                segments.add(
                    RouteDraft.DraftSegment(
                        id = segmentId,
                        fromNodeId = fromId,
                        toNodeId = toId,
                        lengthMm = length.toFloat(),
                        start = fromWorld,
                        end = toWorld,
                    ),
                )
                edges.add(
                    RouteDraft.DraftEdge(
                        id = "e-${edgeSeq++}",
                        fromNodeId = fromId,
                        toNodeId = toId,
                        segmentId = segmentId,
                        preferred = true,
                    ),
                )
                previousNodeId = toId
            }
        }

        // Promote degree>2 waypoints to JUNCTION.
        val degree = mutableMapOf<String, Int>()
        for (edge in edges) {
            degree[edge.fromNodeId] = (degree[edge.fromNodeId] ?: 0) + 1
            degree[edge.toNodeId] = (degree[edge.toNodeId] ?: 0) + 1
        }
        val normalizedNodes = nodes.map { node ->
            val d = degree[node.id] ?: 0
            when {
                node.type == RouteNodeType.START || node.type == RouteNodeType.END -> node
                d >= 3 -> node.copy(type = RouteNodeType.JUNCTION)
                else -> node
            }
        }

        return RouteDraft(
            name = routeName,
            nodes = normalizedNodes,
            segments = segments,
            edges = edges,
        )
    }

    private fun distance(a: WorldCoordinate, b: WorldCoordinate): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
