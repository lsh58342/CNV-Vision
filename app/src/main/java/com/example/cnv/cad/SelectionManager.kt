package com.example.cnv.cad

import com.example.cnv.core.event.PositionEvent
import com.example.cnv.core.model.RouteDirection
import com.example.cnv.map.Route
import com.example.cnv.map.RouteNodeType
import kotlin.math.hypot

/**
 * Hit-testing and selection state only. Never mutates Route or Inspection.
 */
class SelectionManager(
    private val hitNodeRadiusPx: Float = 28f,
    private val hitSegmentSlopPx: Float = 18f,
) {
    @Volatile
    private var state: SelectionState = SelectionState.EMPTY

    fun state(): SelectionState = state

    fun setErrorSegments(ids: Set<String>) {
        state = state.copy(errorSegmentIds = ids)
    }

    fun clear() {
        state = state.copy(
            selectedNodeId = null,
            selectedSegmentId = null,
            selectedBranchNodeId = null,
            selectionCount = 0,
        )
    }

    /**
     * @return updated state after tap at view coordinates.
     */
    fun selectAt(
        viewX: Float,
        viewY: Float,
        route: Route,
        layout: CADRenderer.Layout,
        camera: CADCamera,
    ): SelectionState {
        val branchHit = hitBranch(viewX, viewY, route, layout, camera)
        if (branchHit != null) {
            state = state.copy(
                selectedBranchNodeId = branchHit,
                selectedNodeId = branchHit,
                selectedSegmentId = null,
                selectionCount = 1,
            )
            return state
        }
        val nodeHit = hitNode(viewX, viewY, layout, camera)
        if (nodeHit != null) {
            state = state.copy(
                selectedNodeId = nodeHit,
                selectedBranchNodeId = null,
                selectedSegmentId = null,
                selectionCount = 1,
            )
            return state
        }
        val segmentHit = hitSegment(viewX, viewY, layout, camera)
        if (segmentHit != null) {
            state = state.copy(
                selectedSegmentId = segmentHit,
                selectedNodeId = null,
                selectedBranchNodeId = null,
                selectionCount = 1,
            )
            return state
        }
        clear()
        return state
    }

    fun selectNode(nodeId: String): SelectionState {
        state = state.copy(
            selectedNodeId = nodeId,
            selectedBranchNodeId = null,
            selectedSegmentId = null,
            selectionCount = 1,
        )
        return state
    }

    fun selectSegment(segmentId: String): SelectionState {
        state = state.copy(
            selectedSegmentId = segmentId,
            selectedNodeId = null,
            selectedBranchNodeId = null,
            selectionCount = 1,
        )
        return state
    }

    fun buildInfo(
        route: Route?,
        positionEvent: PositionEvent?,
        currentPositionText: String,
        inspectionState: String,
    ): SelectionInfo {
        val current = state
        val segment = current.selectedSegmentId?.let { id -> route?.segments?.firstOrNull { it.id == id } }
            ?: positionEvent?.segmentId?.let { id -> route?.segments?.firstOrNull { it.id == id } }
        val nodeId = current.selectedNodeId
            ?: current.selectedBranchNodeId
            ?: positionEvent?.nodeId
            ?: "—"
        val direction = positionEvent?.direction?.name
            ?: RouteDirection.FORWARD.name
        return SelectionInfo(
            routeName = route?.name ?: "—",
            segmentId = segment?.id ?: current.selectedSegmentId ?: positionEvent?.segmentId ?: "—",
            segmentLengthMm = segment?.lengthMm ?: 0f,
            direction = direction,
            nodeId = nodeId,
            progress = positionEvent?.progress ?: 0f,
            currentPositionText = currentPositionText,
            inspectionState = inspectionState,
        )
    }

    private fun hitBranch(
        viewX: Float,
        viewY: Float,
        route: Route,
        layout: CADRenderer.Layout,
        camera: CADCamera,
    ): String? {
        var best: String? = null
        var bestDist = hitNodeRadiusPx
        for (id in layout.branchNodeIds) {
            val world = layout.nodeWorld[id] ?: continue
            val node = route.node(id)
            if (node != null && node.type != RouteNodeType.JUNCTION &&
                route.outgoingEdges(id).size <= 1
            ) {
                continue
            }
            val dx = camera.worldToViewX(world.x) - viewX
            val dy = camera.worldToViewY(world.y) - viewY
            val dist = hypot(dx, dy)
            if (dist <= bestDist) {
                bestDist = dist
                best = id
            }
        }
        return best
    }

    private fun hitNode(
        viewX: Float,
        viewY: Float,
        layout: CADRenderer.Layout,
        camera: CADCamera,
    ): String? {
        var best: String? = null
        var bestDist = hitNodeRadiusPx
        for ((id, world) in layout.nodeWorld) {
            val dx = camera.worldToViewX(world.x) - viewX
            val dy = camera.worldToViewY(world.y) - viewY
            val dist = hypot(dx, dy)
            if (dist <= bestDist) {
                bestDist = dist
                best = id
            }
        }
        return best
    }

    private fun hitSegment(
        viewX: Float,
        viewY: Float,
        layout: CADRenderer.Layout,
        camera: CADCamera,
    ): String? {
        var best: String? = null
        var bestDist = hitSegmentSlopPx
        for ((id, pair) in layout.segmentWorld) {
            val x1 = camera.worldToViewX(pair.first.x)
            val y1 = camera.worldToViewY(pair.first.y)
            val x2 = camera.worldToViewX(pair.second.x)
            val y2 = camera.worldToViewY(pair.second.y)
            val dist = distanceToSegment(viewX, viewY, x1, y1, x2, y2)
            if (dist <= bestDist) {
                bestDist = dist
                best = id
            }
        }
        return best
    }

    private fun distanceToSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
    }
}
