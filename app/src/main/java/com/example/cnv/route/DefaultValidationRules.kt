package com.example.cnv.route

import com.example.cnv.map.Route
import java.util.ArrayDeque

/**
 * Built-in read-only validation rules for Map Matching readiness.
 */
object DefaultValidationRules {

    fun all(): List<ValidationRule> = listOf(
        nodeCountRule,
        segmentCountRule,
        branchCountRule,
        isolatedNodeRule,
        unconnectedSegmentRule,
        zeroLengthRule,
        shortSegmentRule,
        duplicateNodeRule,
        duplicateSegmentRule,
        selfLoopRule,
        invalidDirectionRule,
        continuityRule,
        routeLengthRule,
        duplicateRouteIdRule,
    )

    private val nodeCountRule = ValidationRule { route, config ->
        if (route.nodes.size < config.minimumNodeCount) {
            listOf(
                ValidationIssue(
                    type = ValidationIssueType.NODE_COUNT,
                    message = "Node count ${route.nodes.size} < minimum ${config.minimumNodeCount}",
                    severity = ValidationSeverity.ERROR,
                ),
            )
        } else {
            emptyList()
        }
    }

    private val segmentCountRule = ValidationRule { route, config ->
        if (route.segments.size < config.minimumSegmentCount) {
            listOf(
                ValidationIssue(
                    type = ValidationIssueType.SEGMENT_COUNT,
                    message = "Segment count ${route.segments.size} < minimum ${config.minimumSegmentCount}",
                    severity = ValidationSeverity.ERROR,
                ),
            )
        } else {
            emptyList()
        }
    }

    private val branchCountRule = ValidationRule { route, config ->
        val branches = route.nodes.count { route.outgoingEdges(it.id).size > 1 }
        if (branches > 0 && config.branchTolerance < 0) {
            listOf(
                ValidationIssue(
                    type = ValidationIssueType.BRANCH_COUNT,
                    message = "Unexpected branch count=$branches",
                    severity = ValidationSeverity.WARNING,
                ),
            )
        } else {
            emptyList()
        }
    }

    private val isolatedNodeRule = ValidationRule { route, _ ->
        val referenced = mutableSetOf<String>()
        route.segments.forEach {
            referenced.add(it.fromNodeId)
            referenced.add(it.toNodeId)
        }
        route.edges.forEach {
            referenced.add(it.fromNodeId)
            referenced.add(it.toNodeId)
        }
        route.nodes.filter { it.id !in referenced }.map { node ->
            ValidationIssue(
                type = ValidationIssueType.ISOLATED_NODE,
                message = "Isolated node ${node.id}",
                severity = ValidationSeverity.ERROR,
                nodeId = node.id,
            )
        }
    }

    private val unconnectedSegmentRule = ValidationRule { route, _ ->
        val nodeIds = route.nodes.map { it.id }.toSet()
        route.segments.mapNotNull { segment ->
            val missingFrom = segment.fromNodeId !in nodeIds
            val missingTo = segment.toNodeId !in nodeIds
            if (!missingFrom && !missingTo) {
                null
            } else {
                ValidationIssue(
                    type = ValidationIssueType.UNCONNECTED_SEGMENT,
                    message = "Segment ${segment.id} references missing node(s)",
                    severity = ValidationSeverity.ERROR,
                    segmentId = segment.id,
                    nodeId = when {
                        missingFrom -> segment.fromNodeId
                        else -> segment.toNodeId
                    },
                )
            }
        }
    }

    private val zeroLengthRule = ValidationRule { route, _ ->
        route.segments.filter { it.lengthMm <= 0f }.map { segment ->
            ValidationIssue(
                type = ValidationIssueType.ZERO_LENGTH_SEGMENT,
                message = "Segment ${segment.id} has zero length",
                severity = ValidationSeverity.ERROR,
                segmentId = segment.id,
            )
        }
    }

    private val shortSegmentRule = ValidationRule { route, config ->
        route.segments
            .filter { it.lengthMm > 0f && it.lengthMm < config.minimumSegmentLength }
            .map { segment ->
                ValidationIssue(
                    type = ValidationIssueType.SHORT_SEGMENT,
                    message = "Segment ${segment.id} length ${segment.lengthMm} < minimum",
                    severity = ValidationSeverity.WARNING,
                    segmentId = segment.id,
                )
            }
    }

    private val duplicateNodeRule = ValidationRule { route, _ ->
        route.nodes.groupBy { it.id }.filter { it.value.size > 1 }.map { (id, _) ->
            ValidationIssue(
                type = ValidationIssueType.DUPLICATE_NODE,
                message = "Duplicate node id $id",
                severity = ValidationSeverity.ERROR,
                nodeId = id,
            )
        }
    }

    private val duplicateSegmentRule = ValidationRule { route, _ ->
        route.segments.groupBy { it.id }.filter { it.value.size > 1 }.map { (id, _) ->
            ValidationIssue(
                type = ValidationIssueType.DUPLICATE_SEGMENT,
                message = "Duplicate segment id $id",
                severity = ValidationSeverity.ERROR,
                segmentId = id,
            )
        }
    }

    private val selfLoopRule = ValidationRule { route, _ ->
        route.segments.filter { it.fromNodeId == it.toNodeId }.map { segment ->
            ValidationIssue(
                type = ValidationIssueType.SELF_LOOP,
                message = "Self-loop segment ${segment.id}",
                severity = ValidationSeverity.ERROR,
                segmentId = segment.id,
                nodeId = segment.fromNodeId,
            )
        }
    }

    private val invalidDirectionRule = ValidationRule { route, _ ->
        val issues = mutableListOf<ValidationIssue>()
        for (edge in route.edges) {
            val segment = route.segment(edge.segmentId)
            if (segment == null) {
                issues.add(
                    ValidationIssue(
                        type = ValidationIssueType.INVALID_DIRECTION,
                        message = "Edge ${edge.id} points to missing segment ${edge.segmentId}",
                        severity = ValidationSeverity.ERROR,
                        segmentId = edge.segmentId,
                    ),
                )
                continue
            }
            val matchesForward =
                segment.fromNodeId == edge.fromNodeId && segment.toNodeId == edge.toNodeId
            val matchesBackward =
                segment.fromNodeId == edge.toNodeId && segment.toNodeId == edge.fromNodeId
            if (!matchesForward && !matchesBackward) {
                issues.add(
                    ValidationIssue(
                        type = ValidationIssueType.INVALID_DIRECTION,
                        message = "Edge ${edge.id} does not match segment endpoints",
                        severity = ValidationSeverity.ERROR,
                        segmentId = segment.id,
                        nodeId = edge.fromNodeId,
                    ),
                )
            }
        }
        if (route.startNodeId.isBlank() || route.node(route.startNodeId) == null) {
            issues.add(
                ValidationIssue(
                    type = ValidationIssueType.INVALID_DIRECTION,
                    message = "Invalid startNodeId ${route.startNodeId}",
                    severity = ValidationSeverity.ERROR,
                    nodeId = route.startNodeId,
                ),
            )
        }
        if (route.startSegmentId.isBlank() || route.segment(route.startSegmentId) == null) {
            issues.add(
                ValidationIssue(
                    type = ValidationIssueType.INVALID_DIRECTION,
                    message = "Invalid startSegmentId ${route.startSegmentId}",
                    severity = ValidationSeverity.ERROR,
                    segmentId = route.startSegmentId,
                ),
            )
        }
        issues
    }

    private val continuityRule = ValidationRule { route, _ ->
        if (route.nodes.isEmpty()) return@ValidationRule emptyList()
        val adjacency = mutableMapOf<String, MutableSet<String>>()
        route.edges.forEach { edge ->
            adjacency.getOrPut(edge.fromNodeId) { mutableSetOf() }.add(edge.toNodeId)
            adjacency.getOrPut(edge.toNodeId) { mutableSetOf() }.add(edge.fromNodeId)
        }
        route.segments.forEach { segment ->
            adjacency.getOrPut(segment.fromNodeId) { mutableSetOf() }.add(segment.toNodeId)
            adjacency.getOrPut(segment.toNodeId) { mutableSetOf() }.add(segment.fromNodeId)
        }
        val start = route.startNodeId.takeIf { route.node(it) != null } ?: route.nodes.first().id
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(start)
        visited.add(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in adjacency[current].orEmpty()) {
                if (visited.add(next)) queue.add(next)
            }
        }
        val unreachable = route.nodes.map { it.id }.filter { it !in visited }
        if (unreachable.isEmpty()) {
            emptyList()
        } else {
            listOf(
                ValidationIssue(
                    type = ValidationIssueType.CONTINUITY,
                    message = "Route not continuous; unreachable=${unreachable.joinToString()}",
                    severity = ValidationSeverity.ERROR,
                    nodeId = unreachable.first(),
                ),
            )
        }
    }

    private val routeLengthRule = ValidationRule { route, config ->
        val total = route.segments.sumOf { it.lengthMm.toDouble() }
        when {
            total <= 0.0 -> listOf(
                ValidationIssue(
                    type = ValidationIssueType.ROUTE_LENGTH,
                    message = "Total route length is zero",
                    severity = ValidationSeverity.ERROR,
                ),
            )
            total > config.maximumSegmentLength * route.segments.size.coerceAtLeast(1) -> listOf(
                ValidationIssue(
                    type = ValidationIssueType.ROUTE_LENGTH,
                    message = "Total route length $total exceeds expected maximum bound",
                    severity = ValidationSeverity.WARNING,
                ),
            )
            else -> emptyList()
        }
    }

    private val duplicateRouteIdRule = ValidationRule { route, _ ->
        if (route.id.isBlank()) {
            listOf(
                ValidationIssue(
                    type = ValidationIssueType.DUPLICATE_ROUTE_ID,
                    message = "Route id is blank",
                    severity = ValidationSeverity.ERROR,
                ),
            )
        } else {
            emptyList()
        }
    }
}
