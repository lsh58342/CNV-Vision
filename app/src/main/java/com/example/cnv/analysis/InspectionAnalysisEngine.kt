package com.example.cnv.analysis

import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.factory.model.Zone
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.inspection.PersistedInspectionEvent
import com.example.cnv.inspection.RouteSnapshotCodec
import com.example.cnv.map.Route
import com.example.cnv.ui.screen.drawing.RouteHighlightHelper
import kotlin.math.max

/**
 * Inspection Analysis Engine (STEP 17).
 * Sole module that analyzes Inspection Session events into [InspectionAnalysisResult].
 * Does not run AI / Camera / OpenCV / Replay / HeatMap generation.
 */
class InspectionAnalysisEngine(
    private val config: InspectionAnalysisConfig = InspectionAnalysisConfig.DEFAULT,
    private val validationCalculator: ValidationScoreCalculator = ValidationScoreCalculator(),
) {

    fun analyze(input: InspectionAnalysisInput): InspectionAnalysisResult {
        val session = input.session
        val summary = session.summary
        val events = session.events
        val profile = summary.conveyorProfile
        val layout = input.layout
            ?: input.route?.let { r ->
                val snap = RouteSnapshotCodec.decode(input.session.summary.routeSnapshotJson)
                HeatMapRouteLayout.build(r, worldMapper = snap?.toMapper())
            }

        val distance = computeDistance(events, summary.totalDistanceMm)
        val speed = computeSpeed(events, summary.averageSpeedMmPerSec, profile.nominalSpeedMPerMin)
        val tracking = computeTracking(events)
        val shock = computeShock(events, distance.totalDistanceMm, summary.shockCount, summary.maximumShock)
        val zones = computeZones(events, input.zones, input.route, layout)
        val coverage = computeCoverage(
            summaryCoverage = summary.coverage,
            events = events,
            layout = layout,
            heatLayer = input.heatLayer,
            drawingHeatLayerForCoverage = input.drawingHeatLayerForCoverage,
            sessionId = summary.sessionId,
        )
        val validationScore = validationCalculator.compute(
            events = events,
            profile = profile,
            fallbackScore = summary.speedValidation.validationScore,
        )

        return InspectionAnalysisResult(
            sessionId = summary.sessionId,
            drawingId = summary.drawingId,
            summary = InspectionAnalysisSummary.from(summary, events.size),
            distance = distance,
            speed = speed,
            tracking = tracking,
            shock = shock,
            zones = zones,
            coverage = coverage,
            validationScore = validationScore,
            conveyorProfile = profile,
        )
    }

    private fun computeDistance(
        events: List<PersistedInspectionEvent>,
        fallbackTotal: Float,
    ): DistanceStatistics {
        if (events.isEmpty()) {
            return DistanceStatistics(totalDistanceMm = fallbackTotal)
        }
        val deltas = ArrayList<Float>()
        var prev = events.first().distanceMm
        for (i in 1 until events.size) {
            val d = events[i].distanceMm
            val delta = (d - prev).let { if (it >= 0f) it else d }
            deltas.add(delta)
            prev = d
        }
        val total = events.last().distanceMm.takeIf { it > 0f }
            ?: fallbackTotal.takeIf { it > 0f }
            ?: deltas.sum()
        if (deltas.isEmpty()) {
            return DistanceStatistics(totalDistanceMm = total, averageDistanceMm = total)
        }
        return DistanceStatistics(
            totalDistanceMm = total,
            averageDistanceMm = deltas.sum() / deltas.size,
            maximumDeltaMm = deltas.maxOrNull() ?: 0f,
            minimumDeltaMm = deltas.minOrNull() ?: 0f,
        )
    }

    private fun computeSpeed(
        events: List<PersistedInspectionEvent>,
        fallbackAvgMmPerSec: Float,
        nominalMPerMin: Float?,
    ): SpeedStatistics {
        val speeds = ArrayList<Float>()
        for (i in 1 until events.size) {
            val a = events[i - 1]
            val b = events[i]
            val dtSec = ((b.timestampNs - a.timestampNs) / 1_000_000_000.0).toFloat()
            if (dtSec <= 0f) continue
            val dd = (b.distanceMm - a.distanceMm).let { if (it >= 0f) it else b.distanceMm }
            speeds.add(dd / dtSec)
        }
        if (speeds.isEmpty()) {
            return SpeedStatistics(
                averageSpeedMmPerSec = fallbackAvgMmPerSec,
                maximumSpeedMmPerSec = fallbackAvgMmPerSec,
                minimumSpeedMmPerSec = fallbackAvgMmPerSec,
                nominalSpeedMPerMin = nominalMPerMin,
                speedDifferenceMmPerSec = validationCalculator.speedDifferenceMmPerSec(
                    fallbackAvgMmPerSec,
                    nominalMPerMin,
                ),
            )
        }
        val avg = speeds.sum() / speeds.size
        return SpeedStatistics(
            averageSpeedMmPerSec = avg,
            maximumSpeedMmPerSec = speeds.maxOrNull() ?: 0f,
            minimumSpeedMmPerSec = speeds.minOrNull() ?: 0f,
            nominalSpeedMPerMin = nominalMPerMin,
            speedDifferenceMmPerSec = validationCalculator.speedDifferenceMmPerSec(avg, nominalMPerMin),
        )
    }

    private fun computeTracking(events: List<PersistedInspectionEvent>): TrackingStatistics {
        if (events.isEmpty()) return TrackingStatistics.EMPTY
        val confidences = events.map { it.trackingConfidence }.filter { it >= 0f }
        if (confidences.isEmpty()) return TrackingStatistics.EMPTY
        val low = confidences.count { it in 0f..config.lowConfidenceThreshold }
        val loss = confidences.count { it <= config.trackingLossThreshold }
        return TrackingStatistics(
            averageConfidence = confidences.sum() / confidences.size,
            minimumConfidence = confidences.minOrNull() ?: 0f,
            lowConfidenceCount = low,
            trackingLossCount = loss,
        )
    }

    private fun computeShock(
        events: List<PersistedInspectionEvent>,
        totalDistanceMm: Float,
        fallbackCount: Int,
        fallbackMax: Float,
    ): ShockStatistics {
        val shocks = events.filter { it.hasShock }
        val count = shocks.size.takeIf { it > 0 } ?: fallbackCount
        val strengths = shocks.map { it.shockStrength }.filter { it > 0f }
        val maxShock = strengths.maxOrNull() ?: fallbackMax
        val avg = if (strengths.isNotEmpty()) strengths.sum() / strengths.size else 0f
        val meters = (totalDistanceMm / 1000f).coerceAtLeast(0.001f)
        return ShockStatistics(
            shockCount = count,
            maximumShock = maxShock,
            averageShock = avg,
            shockDensityPerMeter = count / meters,
        )
    }

    private fun computeZones(
        events: List<PersistedInspectionEvent>,
        zones: List<Zone>,
        route: Route?,
        layout: HeatMapRouteLayout.LayoutResult?,
    ): List<ZoneStatistics> {
        if (zones.isEmpty() || route == null || layout == null || events.isEmpty()) return emptyList()
        val ranges = zones.mapNotNull { zoneRange(it, route, layout) }
        if (ranges.isEmpty()) return emptyList()

        data class Acc(
            var distance: Float = 0f,
            var shocks: Int = 0,
            var timeMs: Long = 0L,
            var samples: Int = 0,
        )
        val acc = ranges.associate { it.zoneId to Acc() }.toMutableMap()
        var prevRouteMm: Float? = null
        var prevTs: Long? = null
        var lastSeg: String? = null
        var lastProgress = 0f
        var lastRouteMm = 0f

        for (event in events) {
            val parsed = parseRoute(event.routePosition)
            if (parsed != null) {
                lastSeg = parsed.first
                lastProgress = parsed.second
                lastRouteMm = HeatMapRouteLayout.absoluteRouteMm(layout, lastSeg, lastProgress)
                    ?: lastRouteMm
            } else if (event.distanceMm > 0f) {
                lastRouteMm = event.distanceMm
            }
            val zone = ranges.firstOrNull { lastRouteMm in it.startMm..it.endMm } ?: continue
            val a = acc.getValue(zone.zoneId)
            a.samples += 1
            if (event.hasShock) a.shocks += 1
            val prevMm = prevRouteMm
            if (prevMm != null) {
                a.distance += max(0f, lastRouteMm - prevMm)
            }
            val pts = prevTs
            if (pts != null && event.timestampNs > pts) {
                a.timeMs += (event.timestampNs - pts) / 1_000_000L
            }
            prevRouteMm = lastRouteMm
            prevTs = event.timestampNs
        }

        return ranges.map { range ->
            val a = acc.getValue(range.zoneId)
            val span = (range.endMm - range.startMm).coerceAtLeast(0.001f)
            ZoneStatistics(
                zoneId = range.zoneId,
                zoneName = range.zoneName,
                distanceMm = a.distance,
                shockCount = a.shocks,
                coverage = (a.distance / span).coerceIn(0f, 1f),
                inspectionTimeMs = a.timeMs,
            )
        }
    }

    private fun computeCoverage(
        summaryCoverage: Float,
        events: List<PersistedInspectionEvent>,
        layout: HeatMapRouteLayout.LayoutResult?,
        heatLayer: com.example.cnv.heatmap.DrawingHeatLayer?,
        drawingHeatLayerForCoverage: com.example.cnv.heatmap.DrawingHeatLayer?,
        sessionId: String,
    ): CoverageStatistics {
        val routeCoverage = if (layout != null && layout.totalLengthMm > 0f && events.isNotEmpty()) {
            val maxMm = events.maxOf { it.distanceMm }.coerceAtLeast(0f)
            (maxMm / layout.totalLengthMm).coerceIn(0f, 1f)
        } else {
            summaryCoverage.coerceIn(0f, 1f)
        }
        val sessionHeat = heatLayer?.points?.count { it.sessionId == sessionId } ?: 0
        val coverageDenominator = drawingHeatLayerForCoverage ?: heatLayer
        val drawingCoverage = when {
            coverageDenominator != null && coverageDenominator.pointCount > 0 ->
                (sessionHeat.toFloat() / coverageDenominator.pointCount.toFloat()).coerceIn(0f, 1f)
            else -> summaryCoverage.coerceIn(0f, 1f)
        }
        val inspectionRatio = if (events.isEmpty()) {
            0f
        } else {
            ((routeCoverage + drawingCoverage + summaryCoverage.coerceIn(0f, 1f)) / 3f)
                .coerceIn(0f, 1f)
        }
        return CoverageStatistics(
            drawingCoverage = drawingCoverage,
            routeCoverage = routeCoverage,
            inspectionRatio = inspectionRatio,
        )
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
        anchor: RouteAnchor,
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

    private fun parseRoute(raw: String): Pair<String, Float>? {
        if (raw.isBlank()) return null
        val parts = raw.split('|')
        val segmentId = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
        val progress = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
        return segmentId to progress.coerceIn(0f, 1f)
    }
}
