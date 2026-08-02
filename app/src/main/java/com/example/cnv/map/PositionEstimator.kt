package com.example.cnv.map

import com.example.cnv.core.model.RouteDirection
import kotlin.math.abs

/**
 * Rule-based progress along [Route] using incremental travel distance (mm).
 * No GPS / PLC / AI.
 */
class PositionEstimator(
    private val config: MapConfig = MapConfig.DEFAULT,
) {

    private data class State(
        val segmentId: String,
        val distanceFromSegmentStart: Float,
        val direction: RouteDirection,
    )

    private var state: State? = null
    private var lastLoggedSegmentId: String? = null

    fun reset(route: Route) {
        state = State(
            segmentId = route.startSegmentId,
            distanceFromSegmentStart = 0f,
            direction = RouteDirection.FORWARD,
        )
        lastLoggedSegmentId = null
        logEnter(route.startSegmentId, reason = "RESET")
    }

    fun clear() {
        state = null
        lastLoggedSegmentId = null
    }

    /**
     * @param deltaDistanceMm signed travel from Fusion (positive ≈ forward along camera motion).
     */
    fun estimate(
        route: Route,
        deltaDistanceMm: Float,
        timestampNs: Long,
        confidence: Float,
    ): RoutePosition? {
        if (confidence < config.minimumConfidence) {
            println(
                "LOG[MapMatch][SKIP] confidence=${"%.2f".format(confidence)} " +
                    "< min=${config.minimumConfidence} delta=${"%.2f".format(deltaDistanceMm)}",
            )
            return null
        }
        val current = ensureState(route)

        val direction = when {
            deltaDistanceMm < 0f -> RouteDirection.BACKWARD
            else -> RouteDirection.FORWARD
        }
        val travel = abs(deltaDistanceMm)
        if (travel <= 0f) {
            return buildPosition(route, current.copy(direction = direction), timestampNs, confidence)
        }

        val advanced = if (direction == RouteDirection.FORWARD) {
            advanceForward(route, current, travel)
        } else {
            advanceBackward(route, current, travel)
        } ?: return null

        if (advanced.segmentId != lastLoggedSegmentId) {
            lastLoggedSegmentId?.let { logExit(it) }
            logEnter(advanced.segmentId)
            lastLoggedSegmentId = advanced.segmentId
        }

        state = advanced
        return buildPosition(route, advanced, timestampNs, confidence)
    }

    private fun ensureState(route: Route): State {
        val existing = state
        if (existing != null) return existing
        reset(route)
        return state ?: State(
            segmentId = route.startSegmentId,
            distanceFromSegmentStart = 0f,
            direction = RouteDirection.FORWARD,
        )
    }

    /**
     * STEP 20-22: Do **not** absorb overshoot into the current segment when a next edge exists.
     * Old `remaining <= room + matchingTolerance` kept progress clamped at end of segment 0
     * for all small optical-flow deltas (≤50mm), so Tracking never entered segment 1+.
     */
    private fun advanceForward(route: Route, current: State, travelMm: Float): State? {
        var remaining = travelMm
        var segmentId = current.segmentId
        var distance = current.distanceFromSegmentStart

        var guard = 0
        while (remaining > 0f && guard < MAX_SEGMENT_HOPS) {
            guard++
            val segment = route.segment(segmentId) ?: return null
            val room = (segment.lengthMm - distance).coerceAtLeast(0f)

            if (remaining <= room) {
                return State(
                    segmentId = segmentId,
                    distanceFromSegmentStart = distance + remaining,
                    direction = RouteDirection.FORWARD,
                )
            }

            // Consume remainder of this segment, then hop if topology allows.
            remaining -= room
            val nextEdge = route.preferredOutgoingEdge(segment.toNodeId)
            if (nextEdge == null || nextEdge.segmentId == segmentId) {
                // Dead-end: clamp at end. Tolerate tiny residual without rejecting frame.
                if (remaining > config.maximumPositionErrorMm) {
                    println(
                        "LOG[MapMatch][DEAD_END] seg=$segmentId remaining=${"%.1f".format(remaining)} " +
                            "noOutgoing from=${segment.toNodeId}",
                    )
                }
                return State(segmentId, segment.lengthMm, RouteDirection.FORWARD)
            }
            println(
                "LOG[MapMatch][HOP] $segmentId → ${nextEdge.segmentId} " +
                    "carryMm=${"%.2f".format(remaining)} via=${segment.toNodeId}",
            )
            segmentId = nextEdge.segmentId
            distance = 0f
            // Tiny leftovers at nodes are noise — drop within branch tolerance after hop.
            if (remaining <= config.branchToleranceMm) {
                remaining = 0f
            }
        }
        return State(segmentId, distance, RouteDirection.FORWARD)
    }

    private fun advanceBackward(route: Route, current: State, travelMm: Float): State? {
        var remaining = travelMm
        var segmentId = current.segmentId
        var distance = current.distanceFromSegmentStart

        var guard = 0
        while (remaining > 0f && guard < MAX_SEGMENT_HOPS) {
            guard++
            val segment = route.segment(segmentId) ?: return null
            if (remaining <= distance) {
                return State(segmentId, distance - remaining, RouteDirection.BACKWARD)
            }

            remaining -= distance.coerceAtLeast(0f)
            val previous = findIncomingPreferred(route, segment.fromNodeId)
            if (previous == null) {
                return State(segmentId, 0f, RouteDirection.BACKWARD)
            }
            val prevSegment = route.segment(previous.segmentId) ?: return null
            println(
                "LOG[MapMatch][HOP_BACK] $segmentId → ${previous.segmentId} " +
                    "carryMm=${"%.2f".format(remaining)}",
            )
            segmentId = previous.segmentId
            distance = prevSegment.lengthMm
            if (remaining <= config.nodeRadiusMm) {
                remaining = 0f
            }
        }
        return State(segmentId, distance, RouteDirection.BACKWARD)
    }

    private fun findIncomingPreferred(route: Route, toNodeId: String): RouteEdge? {
        val incoming = route.edges.filter { it.toNodeId == toNodeId }
        return incoming.firstOrNull { it.preferred } ?: incoming.firstOrNull()
    }

    private fun buildPosition(
        route: Route,
        state: State,
        timestampNs: Long,
        confidence: Float,
    ): RoutePosition? {
        val segment = route.segment(state.segmentId) ?: return null
        val progress = if (segment.lengthMm <= 0f) {
            0f
        } else {
            (state.distanceFromSegmentStart / segment.lengthMm).coerceIn(0f, 1f)
        }
        val nearStart = state.distanceFromSegmentStart <= config.nodeRadiusMm
        val nearEnd =
            segment.lengthMm - state.distanceFromSegmentStart <= config.nodeRadiusMm
        val nodeId = when {
            nearStart -> segment.fromNodeId
            nearEnd -> segment.toNodeId
            else -> segment.fromNodeId
        }
        return RoutePosition(
            segmentId = state.segmentId,
            nodeId = nodeId,
            distanceFromSegmentStart = state.distanceFromSegmentStart,
            progress = progress,
            direction = state.direction,
            timestampNs = timestampNs,
            confidence = confidence,
        )
    }

    private fun logEnter(segmentId: String, reason: String = "TRANSITION") {
        println("ENTER SEGMENT $segmentId ($reason)")
        println("LOG[RouteTransition] ENTER SEGMENT $segmentId")
    }

    private fun logExit(segmentId: String) {
        println("EXIT SEGMENT $segmentId")
        println("LOG[RouteTransition] EXIT SEGMENT $segmentId")
    }

    companion object {
        private const val MAX_SEGMENT_HOPS = 32
    }
}
