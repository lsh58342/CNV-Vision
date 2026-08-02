package com.example.cnv.heatmap

import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper

/**
 * Generates Drawing [DrawingHeatPoint]s from persisted Inspection Session events (STEP 14).
 * Does not touch Camera / OpenCV / Fusion / Inspection algorithms.
 * Viewer must not call this — only [factory.repository.HeatMapRepository] orchestration.
 */
class HeatMapGenerator(
    private val intensityConfig: HeatMapIntensityConfig = HeatMapIntensityConfig.DEFAULT,
) {

    /**
     * @param mapper optional prebuilt mapper (e.g. from RouteGenerator); falls back to [HeatMapRouteLayout].
     */
    fun generatePoints(
        sessions: List<PersistedInspectionSession>,
        route: Route,
        mapper: CoordinateMapper? = null,
    ): List<DrawingHeatPoint> {
        val baseLayout = HeatMapRouteLayout.build(route) ?: return emptyList()
        val layout = if (mapper != null) {
            HeatMapRouteLayout.LayoutResult(
                mapper = mapper,
                segmentStartMm = baseLayout.segmentStartMm,
                totalLengthMm = baseLayout.totalLengthMm,
            )
        } else {
            baseLayout
        }

        val out = ArrayList<DrawingHeatPoint>()
        for (session in sessions) {
            out.addAll(pointsForSession(session, layout))
        }
        return out
    }

    fun generateLayer(
        drawingId: String,
        sessions: List<PersistedInspectionSession>,
        route: Route,
        mapper: CoordinateMapper? = null,
    ): DrawingHeatLayer {
        val points = generatePoints(sessions, route, mapper)
        return DrawingHeatLayer(
            drawingId = drawingId,
            points = points,
            sourceSessionIds = sessions.map { it.summary.sessionId }.distinct(),
        )
    }

    private fun pointsForSession(
        session: PersistedInspectionSession,
        layout: HeatMapRouteLayout.LayoutResult,
    ): List<DrawingHeatPoint> {
        val sessionId = session.summary.sessionId
        val out = ArrayList<DrawingHeatPoint>()
        var lastSegmentId: String? = null
        var lastProgress = 0f
        var lastRouteMm = 0f

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

            val segmentId = lastSegmentId ?: continue
            val world = HeatMapRouteLayout.toDrawingCoordinate(layout, segmentId, lastProgress)
                ?: continue // off-route — skip

            val intensity = intensityConfig.intensityFor(event.shockStrength, event.hasShock)
            val strength = if (event.hasShock) {
                event.shockStrength
            } else {
                intensityConfig.baseNoShockStrength
            }

            out.add(
                DrawingHeatPoint(
                    drawingX = world.x,
                    drawingY = world.y,
                    shockStrength = strength,
                    intensity = intensity,
                    timestampNs = event.timestampNs,
                    routePositionMm = lastRouteMm,
                    routePositionLabel = event.routePosition.ifBlank {
                        "$segmentId@${"%.2f".format(lastProgress)}"
                    },
                    sessionId = sessionId,
                ),
            )
        }
        return out
    }

    private data class ParsedRoute(val segmentId: String, val nodeId: String, val progress: Float)

    private fun parseRoutePosition(raw: String): ParsedRoute? {
        if (raw.isBlank()) return null
        val parts = raw.split('|')
        if (parts.isEmpty()) return null
        val segmentId = parts[0].takeIf { it.isNotBlank() } ?: return null
        val nodeId = parts.getOrNull(1).orEmpty()
        val progress = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
        return ParsedRoute(segmentId, nodeId, progress.coerceIn(0f, 1f))
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
