package com.example.cnv.debug

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.cnv.map.Route
import com.example.cnv.map.RoutePosition
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.ValidationIssue
import com.example.cnv.route.ValidationSeverity
import com.example.cnv.route.WorldCoordinate
import kotlin.math.hypot

/**
 * Draws route topology on a black canvas (no CAD).
 */
class RouteDebugRenderer(
    private val config: RouteDebugConfig = RouteDebugConfig.DEFAULT,
) {

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = config.segmentWidthPx
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val positionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLUE
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = config.segmentWidthPx
        color = Color.LTGRAY
    }

    data class Layout(
        val nodeWorld: Map<String, WorldCoordinate>,
        val segmentWorld: Map<String, Pair<WorldCoordinate, WorldCoordinate>>,
    )

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
        issues: List<ValidationIssue>,
        selectedNodeId: String?,
        selectedSegmentId: String?,
        currentPosition: RoutePosition?,
        mapper: CoordinateMapper?,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        canvas.drawColor(Color.BLACK)
        val errorSegments = issues.filter {
            it.severity == ValidationSeverity.ERROR && it.segmentId != null
        }.mapNotNull { it.segmentId }.toSet()
        val warningSegments = issues.filter {
            it.severity == ValidationSeverity.WARNING && it.segmentId != null
        }.mapNotNull { it.segmentId }.toSet()
        val errorNodes = issues.mapNotNull { it.nodeId }.toSet()

        for (segment in route.segments) {
            val pair = layout.segmentWorld[segment.id] ?: continue
            segmentPaint.color = when {
                segment.id == selectedSegmentId -> Color.CYAN
                segment.id in errorSegments -> Color.RED
                segment.id in warningSegments -> Color.YELLOW
                else -> Color.GREEN
            }
            val x1 = worldToViewX(pair.first.x, scale, offsetX)
            val y1 = worldToViewY(pair.first.y, scale, offsetY)
            val x2 = worldToViewX(pair.second.x, scale, offsetX)
            val y2 = worldToViewY(pair.second.y, scale, offsetY)
            canvas.drawLine(x1, y1, x2, y2, segmentPaint)
            drawDirectionArrow(canvas, x1, y1, x2, y2)
        }

        for (node in route.nodes) {
            val world = layout.nodeWorld[node.id] ?: continue
            nodePaint.color = when {
                node.id == selectedNodeId -> Color.CYAN
                node.id in errorNodes -> Color.RED
                route.outgoingEdges(node.id).size > 1 -> Color.MAGENTA
                else -> Color.WHITE
            }
            canvas.drawCircle(
                worldToViewX(world.x, scale, offsetX),
                worldToViewY(world.y, scale, offsetY),
                config.nodeRadiusPx,
                nodePaint,
            )
        }

        if (currentPosition != null) {
            val world = mapper?.toWorld(currentPosition)
                ?: layout.segmentWorld[currentPosition.segmentId]?.let { (start, end) ->
                    val t = currentPosition.progress.coerceIn(0f, 1f).toDouble()
                    WorldCoordinate(
                        x = start.x + (end.x - start.x) * t,
                        y = start.y + (end.y - start.y) * t,
                    )
                }
            if (world != null) {
                canvas.drawCircle(
                    worldToViewX(world.x, scale, offsetX),
                    worldToViewY(world.y, scale, offsetY),
                    config.currentPositionRadiusPx,
                    positionPaint,
                )
            }
        }
    }

    fun hitTestNode(
        layout: Layout,
        viewX: Float,
        viewY: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): String? {
        var bestId: String? = null
        var bestDist = config.nodeRadiusPx * 2f
        for ((id, world) in layout.nodeWorld) {
            val dx = worldToViewX(world.x, scale, offsetX) - viewX
            val dy = worldToViewY(world.y, scale, offsetY) - viewY
            val dist = hypot(dx, dy)
            if (dist <= bestDist) {
                bestDist = dist
                bestId = id
            }
        }
        return bestId
    }

    fun hitTestSegment(
        layout: Layout,
        viewX: Float,
        viewY: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): String? {
        var bestId: String? = null
        var bestDist = config.segmentWidthPx * 3f
        for ((id, pair) in layout.segmentWorld) {
            val x1 = worldToViewX(pair.first.x, scale, offsetX)
            val y1 = worldToViewY(pair.first.y, scale, offsetY)
            val x2 = worldToViewX(pair.second.x, scale, offsetX)
            val y2 = worldToViewY(pair.second.y, scale, offsetY)
            val dist = distanceToSegment(viewX, viewY, x1, y1, x2, y2)
            if (dist <= bestDist) {
                bestDist = dist
                bestId = id
            }
        }
        return bestId
    }

    private fun buildFromMapper(route: Route, mapper: CoordinateMapper): Layout? {
        val segmentWorld = mutableMapOf<String, Pair<WorldCoordinate, WorldCoordinate>>()
        val nodeWorld = mutableMapOf<String, WorldCoordinate>()
        for (segment in route.segments) {
            val geometry = mapper.let { m ->
                // Access via progress endpoints through public API only.
                val startPos = com.example.cnv.map.RoutePosition(
                    segmentId = segment.id,
                    nodeId = segment.fromNodeId,
                    distanceFromSegmentStart = 0f,
                    progress = 0f,
                    direction = com.example.cnv.map.RouteDirection.FORWARD,
                    timestampNs = 0L,
                    confidence = 1f,
                )
                val endPos = startPos.copy(progress = 1f, distanceFromSegmentStart = segment.lengthMm)
                val start = m.toWorld(startPos)
                val end = m.toWorld(endPos)
                if (start != null && end != null) start to end else null
            } ?: continue
            segmentWorld[segment.id] = geometry
            nodeWorld.putIfAbsent(segment.fromNodeId, geometry.first)
            nodeWorld[segment.toNodeId] = geometry.second
        }
        if (segmentWorld.isEmpty()) return null
        return Layout(nodeWorld, segmentWorld)
    }

    private fun buildFallbackLayout(route: Route): Layout {
        val nodeWorld = LinkedHashMap<String, WorldCoordinate>()
        val segmentWorld = LinkedHashMap<String, Pair<WorldCoordinate, WorldCoordinate>>()
        var cursorX = 0.0
        val start = route.startNodeId
        val ordered = linkedSetOf(start)
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
        return Layout(nodeWorld, segmentWorld)
    }

    private fun drawDirectionArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val mx = (x1 + x2) / 2f
        val my = (y1 + y2) / 2f
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len < 1f) return
        val ux = dx / len
        val uy = dy / len
        val size = 12f
        canvas.drawLine(mx, my, mx - ux * size - uy * size * 0.5f, my - uy * size + ux * size * 0.5f, arrowPaint)
        canvas.drawLine(mx, my, mx - ux * size + uy * size * 0.5f, my - uy * size - ux * size * 0.5f, arrowPaint)
    }

    private fun worldToViewX(x: Double, scale: Float, offsetX: Float): Float =
        (x * scale).toFloat() + offsetX

    private fun worldToViewY(y: Double, scale: Float, offsetY: Float): Float =
        (y * scale).toFloat() + offsetY

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
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }
}
