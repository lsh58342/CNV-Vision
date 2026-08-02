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
            pickProgress = 0f,
            pickWorldX = null,
            pickWorldY = null,
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
            val world = layout.nodeWorld[branchHit]
            state = state.copy(
                selectedBranchNodeId = branchHit,
                selectedNodeId = branchHit,
                selectedSegmentId = null,
                pickProgress = progressForNode(branchHit, route, layout),
                pickWorldX = world?.x,
                pickWorldY = world?.y,
                selectionCount = 1,
            )
            return state
        }
        val nodeHit = hitNode(viewX, viewY, layout, camera)
        if (nodeHit != null) {
            val world = layout.nodeWorld[nodeHit]
            state = state.copy(
                selectedNodeId = nodeHit,
                selectedBranchNodeId = null,
                selectedSegmentId = null,
                pickProgress = progressForNode(nodeHit, route, layout),
                pickWorldX = world?.x,
                pickWorldY = world?.y,
                selectionCount = 1,
            )
            return state
        }
        val segmentHit = hitSegment(viewX, viewY, layout, camera)
        if (segmentHit != null) {
            state = state.copy(
                selectedSegmentId = segmentHit.segmentId,
                selectedNodeId = null,
                selectedBranchNodeId = null,
                pickProgress = segmentHit.progress,
                pickWorldX = segmentHit.worldX,
                pickWorldY = segmentHit.worldY,
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
            pickProgress = 0f,
            pickWorldX = null,
            pickWorldY = null,
            selectionCount = 1,
        )
        return state
    }

    fun selectSegment(segmentId: String): SelectionState {
        state = state.copy(
            selectedSegmentId = segmentId,
            selectedNodeId = null,
            selectedBranchNodeId = null,
            pickProgress = 0f,
            pickWorldX = null,
            pickWorldY = null,
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
        val progress = when {
            current.hasSelection -> current.pickProgress
            else -> positionEvent?.progress ?: 0f
        }
        return SelectionInfo(
            routeName = route?.name ?: "—",
            segmentId = segment?.id ?: current.selectedSegmentId ?: positionEvent?.segmentId ?: "—",
            segmentLengthMm = segment?.lengthMm ?: 0f,
            direction = direction,
            nodeId = nodeId,
            progress = progress,
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
    ): SegmentHit? {
        var best: SegmentHit? = null
        var bestDist = hitSegmentSlopPx
        for ((id, pair) in layout.segmentWorld) {
            val x1 = camera.worldToViewX(pair.first.x)
            val y1 = camera.worldToViewY(pair.first.y)
            val x2 = camera.worldToViewX(pair.second.x)
            val y2 = camera.worldToViewY(pair.second.y)
            val projected = projectToSegment(viewX, viewY, x1, y1, x2, y2)
            if (projected.distance <= bestDist) {
                bestDist = projected.distance
                val worldX = pair.first.x + (pair.second.x - pair.first.x) * projected.t
                val worldY = pair.first.y + (pair.second.y - pair.first.y) * projected.t
                best = SegmentHit(
                    segmentId = id,
                    progress = projected.t,
                    worldX = worldX,
                    worldY = worldY,
                )
            }
        }
        return best
    }

    private fun progressForNode(
        nodeId: String,
        route: Route,
        layout: CADRenderer.Layout,
    ): Float {
        if (nodeId == route.startNodeId || nodeId == layout.startNodeId) return 0f
        val startSeg = route.segments.firstOrNull { it.id == route.startSegmentId }
        if (startSeg != null && nodeId == startSeg.toNodeId) return 1f
        return 0f
    }

    private fun projectToSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Projected {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) return Projected(0f, hypot(px - x1, py - y1))
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return Projected(t, hypot(px - (x1 + t * dx), py - (y1 + t * dy)))
    }

    private fun distanceToSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Float = projectToSegment(px, py, x1, y1, x2, y2).distance

    private data class SegmentHit(
        val segmentId: String,
        val progress: Float,
        val worldX: Double,
        val worldY: Double,
    )

    private data class Projected(val t: Float, val distance: Float)
}
