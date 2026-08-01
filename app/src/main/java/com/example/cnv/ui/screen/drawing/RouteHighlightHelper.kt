package com.example.cnv.ui.screen.drawing

import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.map.Route

/**
 * UI-only helper: compute segment ids between two Route anchors for highlight.
 * Does not mutate Route / Map / Core algorithms.
 */
object RouteHighlightHelper {

    fun segmentIdsBetween(route: Route, start: RouteAnchor, end: RouteAnchor): Set<String> {
        val startNode = start.nodeId
            ?: route.segment(start.segmentId.orEmpty())?.fromNodeId
            ?: return emptySet()
        val endNode = end.nodeId
            ?: route.segment(end.segmentId.orEmpty())?.toNodeId
            ?: return emptySet()
        if (startNode == endNode) {
            return start.segmentId?.let { setOf(it) }
                ?: end.segmentId?.let { setOf(it) }
                ?: emptySet()
        }
        val parent = LinkedHashMap<String, Pair<String, String>>() // node -> (prevNode, viaSegment)
        val queue = ArrayDeque<String>()
        queue.add(startNode)
        val visited = mutableSetOf(startNode)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node == endNode) break
            for (edge in route.outgoingEdges(node)) {
                val next = edge.toNodeId
                if (next in visited) continue
                visited.add(next)
                parent[next] = node to edge.segmentId
                queue.add(next)
            }
        }
        if (endNode !in parent && endNode != startNode) {
            // Fallback: highlight explicit segment ids if present.
            return listOfNotNull(start.segmentId, end.segmentId).toSet()
        }
        val segments = LinkedHashSet<String>()
        var cur = endNode
        while (cur != startNode) {
            val step = parent[cur] ?: break
            segments.add(step.second)
            cur = step.first
        }
        if (segments.isEmpty()) {
            listOfNotNull(start.segmentId, end.segmentId).forEach { segments.add(it) }
        }
        return segments
    }
}
