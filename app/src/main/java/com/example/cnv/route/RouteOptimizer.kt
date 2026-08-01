package com.example.cnv.route

import com.example.cnv.map.Route
import com.example.cnv.map.RouteEdge
import com.example.cnv.map.RouteNode
import com.example.cnv.map.RouteNodeType
import com.example.cnv.map.RouteSegment
import kotlin.math.sqrt

/**
 * Normalization only — does not invent topology or recompute paths.
 *
 * Allowed: same-coordinate node merge, short segment drop, direction normalize, ID reorder.
 * Forbidden: AI edits, auto branch creation, auto reconnect, structural redesign.
 */
class RouteOptimizer(
    private val config: RouteConfig = RouteConfig.DEFAULT,
) {

    data class OptimizedRoute(
        val route: Route,
        val segmentGeometry: Map<String, CoordinateMapper.SegmentGeometry>,
        val totalLengthMm: Float,
        val branchCount: Int,
    )

    fun normalize(draft: RouteDraft): OptimizedRoute? {
        if (draft.segments.isEmpty() || draft.nodes.isEmpty()) return null

        // 1) Merge nodes within snapTolerance by rewriting ids.
        val mergeMap = buildNodeMergeMap(draft.nodes)
        var nodes = draft.nodes
            .groupBy { mergeMap[it.id] ?: it.id }
            .map { (id, group) ->
                val primary = group.first()
                primary.copy(id = id, label = id)
            }
        var segments = draft.segments.mapNotNull { segment ->
            val from = mergeMap[segment.fromNodeId] ?: segment.fromNodeId
            val to = mergeMap[segment.toNodeId] ?: segment.toNodeId
            if (from == to) return@mapNotNull null
            segment.copy(fromNodeId = from, toNodeId = to)
        }
        var edges = draft.edges.mapNotNull { edge ->
            val from = mergeMap[edge.fromNodeId] ?: edge.fromNodeId
            val to = mergeMap[edge.toNodeId] ?: edge.toNodeId
            if (from == to) return@mapNotNull null
            edge.copy(fromNodeId = from, toNodeId = to)
        }

        // 2) Drop very short segments (config).
        val keptSegmentIds = segments
            .filter { it.lengthMm >= config.minimumSegmentLength }
            .map { it.id }
            .toSet()
        segments = segments.filter { it.id in keptSegmentIds }
        edges = edges.filter { it.segmentId in keptSegmentIds }

        // 3) Direction normalize: ensure length matches endpoints; flip if inverted within tolerance.
        segments = segments.map { segment ->
            val measured = distance(segment.start, segment.end).toFloat()
            val oriented = if (
                measured > 0f &&
                kotlin.math.abs(measured - segment.lengthMm) <= config.directionTolerance
            ) {
                segment.copy(lengthMm = measured.coerceAtLeast(config.minimumSegmentLength.toFloat()))
            } else {
                segment.copy(lengthMm = measured.coerceAtLeast(config.minimumSegmentLength.toFloat()))
            }
            oriented
        }

        // Drop nodes no longer referenced.
        val referenced = mutableSetOf<String>()
        segments.forEach {
            referenced.add(it.fromNodeId)
            referenced.add(it.toNodeId)
        }
        nodes = nodes.filter { it.id in referenced }
        if (nodes.isEmpty() || segments.isEmpty()) return null

        // 4) Reorder / reassign stable IDs.
        val nodeIdMap = nodes.mapIndexed { index, node -> node.id to "node-${index + 1}" }.toMap()
        val segmentIdMap = segments.mapIndexed { index, segment -> segment.id to "seg-${index + 1}" }.toMap()
        val reNodes = nodes.mapIndexed { index, node ->
            val newId = nodeIdMap.getValue(node.id)
            val type = when {
                index == 0 && node.type == RouteNodeType.START -> RouteNodeType.START
                node.type == RouteNodeType.END -> RouteNodeType.END
                node.type == RouteNodeType.JUNCTION -> RouteNodeType.JUNCTION
                else -> node.type
            }
            RouteDraft.DraftNode(newId, type, newId, node.world)
        }
        val reSegments = segments.map { segment ->
            segment.copy(
                id = segmentIdMap.getValue(segment.id),
                fromNodeId = nodeIdMap.getValue(segment.fromNodeId),
                toNodeId = nodeIdMap.getValue(segment.toNodeId),
            )
        }
        val reEdges = edges.mapIndexed { index, edge ->
            edge.copy(
                id = "edge-${index + 1}",
                fromNodeId = nodeIdMap.getValue(edge.fromNodeId),
                toNodeId = nodeIdMap.getValue(edge.toNodeId),
                segmentId = segmentIdMap.getValue(edge.segmentId),
            )
        }

        val startNodeId = reNodes.firstOrNull { it.type == RouteNodeType.START }?.id
            ?: reSegments.first().fromNodeId
        val startSegmentId = reSegments.firstOrNull { it.fromNodeId == startNodeId }?.id
            ?: reSegments.first().id

        val route = Route(
            id = "route-${draft.name.hashCode().toUInt()}",
            name = draft.name,
            nodes = reNodes.map { RouteNode(it.id, it.type, it.label) },
            segments = reSegments.map {
                RouteSegment(it.id, it.fromNodeId, it.toNodeId, it.lengthMm)
            },
            edges = reEdges.map {
                RouteEdge(it.id, it.fromNodeId, it.toNodeId, it.segmentId, it.preferred)
            },
            startNodeId = startNodeId,
            startSegmentId = startSegmentId,
        )

        val geometry = reSegments.associate { segment ->
            segment.id to CoordinateMapper.SegmentGeometry(
                segmentId = segment.id,
                start = segment.start,
                end = segment.end,
            )
        }
        val totalLength = reSegments.sumOf { it.lengthMm.toDouble() }.toFloat()
        val branchCount = reNodes.count { node ->
            reEdges.count { it.fromNodeId == node.id } > 1
        }

        return OptimizedRoute(
            route = route,
            segmentGeometry = geometry,
            totalLengthMm = totalLength,
            branchCount = branchCount,
        )
    }

    private fun buildNodeMergeMap(nodes: List<RouteDraft.DraftNode>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val survivors = mutableListOf<RouteDraft.DraftNode>()
        for (node in nodes) {
            val match = survivors.firstOrNull {
                distance(it.world, node.world) <= config.snapTolerance
            }
            if (match != null) {
                map[node.id] = match.id
            } else {
                survivors.add(node)
                map[node.id] = node.id
            }
        }
        return map
    }

    private fun distance(a: WorldCoordinate, b: WorldCoordinate): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
