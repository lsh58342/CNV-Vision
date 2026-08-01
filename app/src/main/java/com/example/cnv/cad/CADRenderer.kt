package com.example.cnv.cad

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import com.example.cnv.map.Route
import com.example.cnv.map.RouteNodeType
import com.example.cnv.map.RoutePosition
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate

/**
 * Renders Route geometry + position marker. Does not mutate Route, compute Position,
 * or reference Inspection. Uses [CoordinateMapper] results only.
 */
class CADRenderer(
    private val style: CADStyle = CADStyle.DEFAULT,
) {
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val positionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlay = CADOverlay(style)

    data class Layout(
        val nodeWorld: Map<String, WorldCoordinate>,
        val segmentWorld: Map<String, Pair<WorldCoordinate, WorldCoordinate>>,
        val branchNodeIds: Set<String>,
        val startNodeId: String?,
        val endNodeId: String?,
    )

    data class FrameStats(
        val renderTimeMs: Double,
        val visibleSegmentCount: Int,
        val zoomLevel: Float,
    )

    @Volatile
    var lastStats: FrameStats = FrameStats(0.0, 0, 1f)
        private set

    fun buildLayout(route: Route, mapper: CoordinateMapper?): Layout {
        val fromMapper = mapper?.let { buildFromMapper(route, it) }
        if (fromMapper != null && fromMapper.segmentWorld.isNotEmpty()) {
            return fromMapper
        }
        return buildFallbackLayout(route)
    }

    fun draw(
        canvas: Canvas,
        route: Route,
        layout: Layout,
        camera: CADCamera,
        layers: CADLayerState,
        theme: CADTheme,
        currentWorld: WorldCoordinate?,
        overlayModel: CADOverlayModel,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        val startNs = SystemClock.elapsedRealtimeNanos()
        canvas.drawColor(theme.background)

        if (layers.isEnabled(CADLayer.GRID)) {
            drawGrid(canvas, camera, theme, viewWidth, viewHeight)
        }

        var visibleSegments = 0
        if (layers.isEnabled(CADLayer.ROUTE)) {
            routePaint.applyStroke(theme.route, style.routeStrokePx)
            for (segment in route.segments) {
                val pair = layout.segmentWorld[segment.id] ?: continue
                val x1 = camera.worldToViewX(pair.first.x)
                val y1 = camera.worldToViewY(pair.first.y)
                val x2 = camera.worldToViewX(pair.second.x)
                val y2 = camera.worldToViewY(pair.second.y)
                if (!segmentVisible(x1, y1, x2, y2, viewWidth, viewHeight)) continue
                visibleSegments++
                canvas.drawLine(x1, y1, x2, y2, routePaint)
            }
        }

        val drawNodes = layers.isEnabled(CADLayer.NODE)
        val drawBranches = layers.isEnabled(CADLayer.BRANCH)
        if (drawNodes || drawBranches) {
            for (node in route.nodes) {
                val world = layout.nodeWorld[node.id] ?: continue
                val vx = camera.worldToViewX(world.x)
                val vy = camera.worldToViewY(world.y)
                val isBranch = node.id in layout.branchNodeIds || node.type == RouteNodeType.JUNCTION
                when {
                    (node.id == layout.startNodeId || node.type == RouteNodeType.START) && drawNodes -> {
                        nodePaint.applyFill(theme.startPoint)
                        canvas.drawCircle(vx, vy, style.startEndRadiusPx, nodePaint)
                    }
                    (node.id == layout.endNodeId || node.type == RouteNodeType.END) && drawNodes -> {
                        nodePaint.applyFill(theme.endPoint)
                        canvas.drawCircle(vx, vy, style.startEndRadiusPx, nodePaint)
                    }
                    isBranch && drawBranches -> {
                        nodePaint.applyFill(theme.branch)
                        canvas.drawCircle(vx, vy, style.branchRadiusPx, nodePaint)
                    }
                    !isBranch && drawNodes -> {
                        nodePaint.applyFill(theme.node)
                        canvas.drawCircle(vx, vy, style.nodeRadiusPx, nodePaint)
                    }
                }
            }
        }

        if (layers.isEnabled(CADLayer.POSITION) && currentWorld != null) {
            positionPaint.applyFill(theme.currentPosition)
            canvas.drawCircle(
                camera.worldToViewX(currentWorld.x),
                camera.worldToViewY(currentWorld.y),
                style.positionRadiusPx,
                positionPaint,
            )
        }

        if (layers.isEnabled(CADLayer.DEBUG)) {
            debugPaint.applyText(theme.debugText, style.debugTextSizePx)
            canvas.drawText(
                "segs=${layout.segmentWorld.size} nodes=${layout.nodeWorld.size}",
                16f,
                viewHeight - 24f,
                debugPaint,
            )
        }

        overlay.draw(canvas, overlayModel, theme)

        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
        lastStats = FrameStats(
            renderTimeMs = elapsedMs,
            visibleSegmentCount = visibleSegments,
            zoomLevel = camera.scale,
        )
    }

    private fun drawGrid(
        canvas: Canvas,
        camera: CADCamera,
        theme: CADTheme,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        gridPaint.applyStroke(theme.grid, style.gridStrokePx)
        val worldLeft = camera.viewToWorldX(0f)
        val worldTop = camera.viewToWorldY(0f)
        val worldRight = camera.viewToWorldX(viewWidth)
        val worldBottom = camera.viewToWorldY(viewHeight)
        val step = gridStepForZoom(camera.scale)
        var x = Math.floor(worldLeft / step) * step
        while (x <= worldRight) {
            val vx = camera.worldToViewX(x)
            canvas.drawLine(vx, 0f, vx, viewHeight, gridPaint)
            x += step
        }
        var y = Math.floor(worldTop / step) * step
        while (y <= worldBottom) {
            val vy = camera.worldToViewY(y)
            canvas.drawLine(0f, vy, viewWidth, vy, gridPaint)
            y += step
        }
    }

    private fun gridStepForZoom(scale: Float): Double = when {
        scale < 0.5f -> 200.0
        scale < 1.5f -> 100.0
        scale < 4f -> 50.0
        else -> 25.0
    }

    private fun segmentVisible(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        viewWidth: Float,
        viewHeight: Float,
    ): Boolean {
        val minX = minOf(x1, x2)
        val maxX = maxOf(x1, x2)
        val minY = minOf(y1, y2)
        val maxY = maxOf(y1, y2)
        return maxX >= 0f && minX <= viewWidth && maxY >= 0f && minY <= viewHeight
    }

    private fun buildFromMapper(route: Route, mapper: CoordinateMapper): Layout? {
        val segmentWorld = LinkedHashMap<String, Pair<WorldCoordinate, WorldCoordinate>>()
        val nodeWorld = LinkedHashMap<String, WorldCoordinate>()
        for (segment in route.segments) {
            val startPos = RoutePosition(
                segmentId = segment.id,
                nodeId = segment.fromNodeId,
                distanceFromSegmentStart = 0f,
                progress = 0f,
                direction = com.example.cnv.core.model.RouteDirection.FORWARD,
                timestampNs = 0L,
                confidence = 1f,
            )
            val endPos = startPos.copy(
                progress = 1f,
                distanceFromSegmentStart = segment.lengthMm,
                nodeId = segment.toNodeId,
            )
            val start = mapper.toWorld(startPos) ?: continue
            val end = mapper.toWorld(endPos) ?: continue
            segmentWorld[segment.id] = start to end
            nodeWorld.putIfAbsent(segment.fromNodeId, start)
            nodeWorld[segment.toNodeId] = end
        }
        if (segmentWorld.isEmpty()) return null
        return finalizeLayout(route, nodeWorld, segmentWorld)
    }

    private fun buildFallbackLayout(route: Route): Layout {
        val nodeWorld = LinkedHashMap<String, WorldCoordinate>()
        val segmentWorld = LinkedHashMap<String, Pair<WorldCoordinate, WorldCoordinate>>()
        var cursorX = 0.0
        val ordered = linkedSetOf(route.startNodeId)
        route.segments.forEach {
            ordered.add(it.fromNodeId)
            ordered.add(it.toNodeId)
        }
        route.nodes.forEach { ordered.add(it.id) }
        for (id in ordered) {
            if (id !in nodeWorld) {
                nodeWorld[id] = WorldCoordinate(cursorX, 0.0)
                cursorX += 100.0
            }
        }
        for (segment in route.segments) {
            val from = nodeWorld[segment.fromNodeId] ?: continue
            val to = nodeWorld[segment.toNodeId] ?: continue
            segmentWorld[segment.id] = from to to
        }
        return finalizeLayout(route, nodeWorld, segmentWorld)
    }

    private fun finalizeLayout(
        route: Route,
        nodeWorld: Map<String, WorldCoordinate>,
        segmentWorld: Map<String, Pair<WorldCoordinate, WorldCoordinate>>,
    ): Layout {
        val branchIds = route.nodes
            .filter { route.outgoingEdges(it.id).size > 1 || it.type == RouteNodeType.JUNCTION }
            .map { it.id }
            .toSet()
        val endId = route.nodes.firstOrNull { it.type == RouteNodeType.END }?.id
            ?: route.segments.lastOrNull()?.toNodeId
        return Layout(
            nodeWorld = nodeWorld,
            segmentWorld = segmentWorld,
            branchNodeIds = branchIds,
            startNodeId = route.startNodeId,
            endNodeId = endId,
        )
    }
}
