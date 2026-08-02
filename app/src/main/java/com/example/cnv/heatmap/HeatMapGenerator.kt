package com.example.cnv.heatmap

import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.imu.ShockUnits
import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper
import kotlin.math.roundToInt

/**
 * Generates Drawing [DrawingHeatPoint]s from persisted Inspection Session events.
 * Shock strength is stored in **g**. Points below [ShockUnits.RECORDING_THRESHOLD_G] are skipped.
 */
class HeatMapGenerator(
    private val intensityConfig: HeatMapIntensityConfig = HeatMapIntensityConfig.DEFAULT,
) {

    fun generatePoints(
        sessions: List<PersistedInspectionSession>,
        route: Route,
        mapper: CoordinateMapper? = null,
    ): List<DrawingHeatPoint> {
        val layout = HeatMapRouteLayout.build(route, worldMapper = mapper)
            ?: HeatMapRouteLayout.build(route, worldMapper = null)
            ?: return emptyList()

        val out = ArrayList<DrawingHeatPoint>()
        for (session in sessions) {
            out.addAll(pointsForSession(session, layout))
        }
        val aggregated = aggregateOverlapping(out)
        println(
            "LOG[HeatMapGenerator] sessions=${sessions.size} raw=${out.size} " +
                "aggregated=${aggregated.size} thresholdG=${ShockUnits.RECORDING_THRESHOLD_G}",
        )
        aggregated.forEachIndexed { i, p ->
            if (i < 40 || p.shockStrength >= ShockUnits.RECORDING_THRESHOLD_G) {
                println(
                    "HeatPoint\n" +
                        "X=${"%.1f".format(p.drawingX)}\n" +
                        "Y=${"%.1f".format(p.drawingY)}\n" +
                        "Shock=${"%.2f".format(p.shockStrength)}g\n" +
                        "Timestamp=${p.timestampNs}\n" +
                        "Session=${p.sessionId}",
                )
            }
        }
        return aggregated
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

            if (!event.hasShock) continue
            val shockG = normalizeToG(event.shockStrength)
            if (!ShockUnits.isRecordableG(shockG)) continue

            val segmentId = lastSegmentId ?: continue
            val world = HeatMapRouteLayout.toDrawingCoordinate(layout, segmentId, lastProgress)
                ?: continue

            val intensity = intensityConfig.intensityFor(shockG, hasShock = true)
            out.add(
                DrawingHeatPoint(
                    drawingX = world.x,
                    drawingY = world.y,
                    shockStrength = shockG,
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

    /**
     * Same-location stacks — accumulate shock (max + count boost) for stronger display.
     */
    private fun aggregateOverlapping(points: List<DrawingHeatPoint>): List<DrawingHeatPoint> {
        if (points.isEmpty()) return emptyList()
        val buckets = LinkedHashMap<String, MutableList<DrawingHeatPoint>>()
        for (p in points) {
            val key = "${(p.drawingX / ACCUM_CELL).roundToInt()}_${(p.drawingY / ACCUM_CELL).roundToInt()}_${p.sessionId}"
            buckets.getOrPut(key) { ArrayList() }.add(p)
        }
        return buckets.values.map { group ->
            val first = group.first()
            val maxShock = group.maxOf { it.shockStrength }
            val sumShock = group.sumOf { it.shockStrength.toDouble() }.toFloat()
            val accum = (maxShock + (sumShock - maxShock) * 0.25f).coerceAtLeast(maxShock)
            first.copy(
                shockStrength = accum,
                intensity = intensityConfig.intensityFor(accum, hasShock = true),
                timestampNs = group.maxOf { it.timestampNs },
            )
        }
    }

    /**
     * Accept either g or legacy m/s² values from older sessions.
     */
    private fun normalizeToG(raw: Float): Float {
        if (raw <= 0f) return 0f
        // Legacy normalized 0–1 confidence → treat as unusable for g heat.
        if (raw <= 1.05f) return raw
        // Likely m/s²
        if (raw > 4f) return ShockUnits.ms2ToG(raw)
        return raw
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

    companion object {
        private const val ACCUM_CELL = 8.0
    }
}
