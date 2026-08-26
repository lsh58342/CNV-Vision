package com.example.cnv.replay.internal

import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.map.Route
import com.example.cnv.replay.ReplayFrame
import com.example.cnv.replay.ReplayLoadContext
import com.example.cnv.ui.screen.drawing.RouteHighlightHelper

/**
 * Loads Inspection Session + Events from Room (Engine-only Room access).
 */
internal class ReplaySessionLoader(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
) {

    data class LoadedSession(
        val session: PersistedInspectionSession,
        val frames: List<ReplayFrame>,
    )

    fun loadAsync(
        sessionId: String,
        context: ReplayLoadContext,
        onResult: (Result<LoadedSession>) -> Unit,
    ) {
        InspectionDbGate.submit(
            block = {
                val persisted = catalog.inspections.loadSession(sessionId)
                    ?: error("Session not found: $sessionId")
                if (context.preferredDrawingId != null &&
                    persisted.summary.drawingId != context.preferredDrawingId
                ) {
                    error("Session drawing mismatch")
                }
                val route = context.route
                val zones = context.zones.ifEmpty {
                    catalog.zones.forDrawing(persisted.summary.drawingId)
                }
                val layout = context.layout
                    ?: route?.let {
                        HeatMapRouteLayout.build(
                            it,
                            worldMapper = catalog.routes.underlying().currentMapper(),
                        )
                    }
                val frames = ReplayFrameBuilder.build(
                    session = persisted,
                    route = route,
                    zones = zones,
                    layout = layout,
                )
                LoadedSession(session = persisted, frames = frames)
            },
            onMain = { loaded -> onResult(Result.success(loaded)) },
            onError = { err -> onResult(Result.failure(err)) },
        )
    }
}

/**
 * Builds [ReplayFrame] list once at load time (internal).
 */
internal object ReplayFrameBuilder {

    fun build(
        session: PersistedInspectionSession,
        route: Route?,
        zones: List<Zone>,
        layout: HeatMapRouteLayout.LayoutResult?,
    ): List<ReplayFrame> {
        val sessionId = session.summary.sessionId
        val startNs = session.events.minOfOrNull { it.timestampNs }
            ?: (session.summary.startTimeMs * 1_000_000L)
        val zoneRanges = if (route != null && layout != null) {
            zones.mapNotNull { zone -> zoneRange(zone, route, layout) }
        } else {
            emptyList()
        }

        var lastSegmentId: String? = null
        var lastProgress = 0f
        var lastRouteMm = 0f
        val out = ArrayList<ReplayFrame>(session.events.size)

        session.events.forEachIndexed { i, event ->
            val parsed = parseRoutePosition(event.routePosition)
            if (parsed != null && layout != null) {
                lastSegmentId = parsed.segmentId
                lastProgress = parsed.progress
                lastRouteMm = HeatMapRouteLayout.absoluteRouteMm(
                    layout, parsed.segmentId, parsed.progress,
                ) ?: lastRouteMm
            } else if (event.distanceMm > 0f && layout != null) {
                val located = locateByAbsoluteMm(layout, event.distanceMm)
                if (located != null) {
                    lastSegmentId = located.first
                    lastProgress = located.second
                    lastRouteMm = event.distanceMm
                }
            } else if (event.distanceMm > 0f) {
                lastRouteMm = event.distanceMm
            }

            val world = if (layout != null && lastSegmentId != null) {
                HeatMapRouteLayout.toDrawingCoordinate(layout, lastSegmentId!!, lastProgress)
            } else {
                null
            }
            val zone = zoneRanges.firstOrNull { lastRouteMm in it.startMm..it.endMm }
            out.add(
                ReplayFrame(
                    index = i,
                    eventId = event.id,
                    sessionId = sessionId,
                    timestampNs = event.timestampNs,
                    elapsedMs = ((event.timestampNs - startNs) / 1_000_000L).coerceAtLeast(0L),
                    distanceMm = event.distanceMm.takeIf { it > 0f } ?: lastRouteMm,
                    routePositionMm = lastRouteMm,
                    segmentId = lastSegmentId,
                    progress = lastProgress.takeIf { lastSegmentId != null },
                    drawingX = world?.x,
                    drawingY = world?.y,
                    hasShock = event.hasShock,
                    shockStrength = event.shockStrength,
                    trackingConfidence = event.trackingConfidence,
                    zoneId = zone?.zoneId,
                    zoneName = zone?.zoneName,
                    eventType = event.eventType,
                ),
            )
        }
        return out
    }

    private data class ZoneRange(
        val zoneId: String,
        val zoneName: String,
        val startMm: Float,
        val endMm: Float,
    )

    private fun zoneRange(
        zone: Zone,
        route: Route,
        layout: HeatMapRouteLayout.LayoutResult,
    ): ZoneRange? {
        RouteHighlightHelper.segmentIdsBetween(route, zone.start, zone.end)
        val startMm = resolveAnchorMm(zone.start, layout) ?: return null
        val endMm = resolveAnchorMm(zone.end, layout) ?: startMm
        return ZoneRange(
            zoneId = zone.id,
            zoneName = zone.name,
            startMm = minOf(startMm, endMm),
            endMm = maxOf(startMm, endMm),
        )
    }

    private fun resolveAnchorMm(
        anchor: com.example.cnv.factory.model.RouteAnchor,
        layout: HeatMapRouteLayout.LayoutResult,
    ): Float? {
        val segmentId = anchor.segmentId ?: return null
        val progress = anchor.progress
            ?: anchor.distanceFromSegmentStartMm?.let { dist ->
                val start = layout.segmentStartMm[segmentId] ?: return@let null
                val entries = layout.segmentStartMm.entries.sortedBy { it.value }
                val idx = entries.indexOfFirst { it.key == segmentId }
                val endMm = entries.getOrNull(idx + 1)?.value ?: layout.totalLengthMm
                val length = (endMm - start).coerceAtLeast(0.001f)
                (dist / length).coerceIn(0f, 1f)
            }
            ?: 0f
        return HeatMapRouteLayout.absoluteRouteMm(layout, segmentId, progress)
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
