package com.example.cnv.heatmap

import com.example.cnv.factory.model.Drawing
import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper

/**
 * Builds coordinate-validation debug data using existing [HeatMapRouteLayout] interpolation.
 * Does not change HeatMap generation / blur / gradient algorithms.
 */
object CoordinateValidationBuilder {

    fun build(
        drawing: Drawing,
        route: Route,
        sessions: List<PersistedInspectionSession>,
        mapper: CoordinateMapper? = null,
    ): CoordinateValidationSnapshot {
        val base = HeatMapRouteLayout.build(route, worldMapper = mapper)
            ?: return CoordinateValidationSnapshot.empty(drawing.id)
        val layout = base

        val session = sessions.lastOrNull()
            ?: return CoordinateValidationSnapshot.empty(drawing.id)

        val points = ArrayList<CoordinateDebugPoint>()
        val polyline = ArrayList<Pair<Double, Double>>()
        var lastSegmentId: String? = null
        var lastProgress = 0f
        var lastRouteMm = 0f
        var shockCount = 0
        var offRoute = 0

        // Origin (yellow) — world coords on Drawing (legacy progress supported).
        val originResolved = com.example.cnv.factory.model.OriginCoordinate.resolveWorld(
            drawing = drawing,
            route = route,
            layout = layout,
        )
        val originWorld = originResolved?.let {
            com.example.cnv.route.WorldCoordinate(it.first, it.second)
        }
        if (originWorld != null) {
            val originMm = if (
                com.example.cnv.factory.model.OriginCoordinate.isLegacyProgressPair(
                    drawing.originX,
                    drawing.originY,
                )
            ) {
                HeatMapRouteLayout.absoluteRouteMm(
                    layout,
                    route.startSegmentId,
                    (drawing.originX ?: 0f).coerceIn(0f, 1f),
                ) ?: 0f
            } else {
                0f
            }
            points.add(
                CoordinateDebugPoint(
                    drawingX = originWorld.x,
                    drawingY = originWorld.y,
                    routePositionMm = originMm,
                    routePositionLabel = "ORIGIN",
                    timestampNs = 0L,
                    kind = CoordinateDebugPointKind.ORIGIN,
                    sessionId = session.summary.sessionId,
                    onRoute = true,
                ),
            )
        } else {
            offRoute++
        }

        val eventPoints = ArrayList<CoordinateDebugPoint>()
        for (event in session.events) {
            val parsed = parseRoutePosition(event.routePosition)
            if (parsed != null) {
                lastSegmentId = parsed.segmentId
                lastProgress = parsed.progress
                lastRouteMm = HeatMapRouteLayout.absoluteRouteMm(
                    layout, parsed.segmentId, parsed.progress,
                ) ?: lastRouteMm
            } else if (event.distanceMm > 0f) {
                val located = locateByAbsoluteMm(layout, event.distanceMm)
                if (located != null) {
                    lastSegmentId = located.first
                    lastProgress = located.second
                    lastRouteMm = event.distanceMm
                }
            }

            val segmentId = lastSegmentId
            if (segmentId == null) {
                offRoute++
                continue
            }
            val world = HeatMapRouteLayout.toDrawingCoordinate(layout, segmentId, lastProgress)
            if (world == null) {
                offRoute++
                continue
            }

            val kind = if (event.hasShock) {
                shockCount++
                CoordinateDebugPointKind.SHOCK
            } else {
                CoordinateDebugPointKind.EVENT
            }
            eventPoints.add(
                CoordinateDebugPoint(
                    drawingX = world.x,
                    drawingY = world.y,
                    routePositionMm = lastRouteMm,
                    routePositionLabel = event.routePosition.ifBlank {
                        "$segmentId@${"%.2f".format(lastProgress)}"
                    },
                    timestampNs = event.timestampNs,
                    kind = kind,
                    sessionId = session.summary.sessionId,
                    onRoute = true,
                ),
            )
            polyline.add(world.x to world.y)
        }

        // Mark last event as CURRENT (green) — keep shock/event color secondary via kind override
        if (eventPoints.isNotEmpty()) {
            val last = eventPoints.last()
            eventPoints[eventPoints.lastIndex] = last.copy(kind = CoordinateDebugPointKind.CURRENT)
        }
        points.addAll(eventPoints)

        val direction = when {
            polyline.size >= 2 -> {
                val (x0, y0) = polyline[polyline.size - 2]
                val (x1, y1) = polyline.last()
                val dx = x1 - x0
                when {
                    dx > 1e-3 -> "FORWARD (+X)"
                    dx < -1e-3 -> "BACKWARD (−X)"
                    else -> "STABLE"
                }
            }
            else -> "—"
        }

        val current = eventPoints.lastOrNull()
        val stats = CoordinateValidationStats(
            currentRoutePositionMm = current?.routePositionMm ?: 0f,
            currentDrawingX = current?.drawingX ?: originWorld?.x ?: 0.0,
            currentDrawingY = current?.drawingY ?: originWorld?.y ?: 0.0,
            inspectionDistanceMm = session.summary.totalDistanceMm,
            eventCount = eventPoints.size,
            shockCount = shockCount,
            offRouteCount = offRoute,
            directionLabel = direction,
        )

        return CoordinateValidationSnapshot(
            drawingId = drawing.id,
            points = points,
            polyline = polyline,
            stats = stats,
            sessionId = session.summary.sessionId,
        )
    }

    private data class ParsedRoute(val segmentId: String, val progress: Float)

    private fun parseRoutePosition(raw: String): ParsedRoute? {
        if (raw.isBlank()) return null
        val parts = raw.split('|')
        val segmentId = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val progress = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
        return ParsedRoute(segmentId, progress.coerceIn(0f, 1f))
    }

    private fun locateByAbsoluteMm(
        layout: HeatMapRouteLayout.LayoutResult,
        absoluteMm: Float,
    ): Pair<String, Float>? {
        val entries = layout.segmentStartMm.entries.sortedBy { it.value }
        for (i in entries.indices) {
            val (segmentId, startMm) = entries[i]
            val endMm = entries.getOrNull(i + 1)?.value ?: layout.totalLengthMm
            val length = (endMm - startMm).coerceAtLeast(0.001f)
            if (absoluteMm <= endMm || i == entries.lastIndex) {
                val local = ((absoluteMm - startMm) / length).coerceIn(0f, 1f)
                return segmentId to local
            }
        }
        return null
    }
}
