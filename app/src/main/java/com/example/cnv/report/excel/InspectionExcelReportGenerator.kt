package com.example.cnv.report.excel

import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.inspection.PersistedInspectionEvent
import com.example.cnv.rule.InspectionRuleResult
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Builds Inspection Excel workbook from Repository data only (STEP 19-1).
 * Does not re-analyze events, re-evaluate rules, or regenerate HeatMap.
 */
class InspectionExcelReportGenerator {

    data class Context(
        val buildingName: String = "",
        val floorName: String = "",
        val drawingName: String = "",
    )

    data class Input(
        val analysis: InspectionAnalysisResult,
        val rules: InspectionRuleResult,
        val events: List<PersistedInspectionEvent>,
        val heatPoints: List<DrawingHeatPoint>,
        val context: Context = Context(),
    )

    fun write(input: Input, output: OutputStream) {
        val writer = XlsxWorkbookWriter()
            .addSheet("Summary", buildSummary(input))
            .addSheet("Zone Summary", buildZoneSummary(input))
            .addSheet("Rule Result", buildRuleResult(input))
            .addSheet("Inspection Events", buildEvents(input))
        writer.write(output)
    }

    private fun buildSummary(input: Input): List<List<Any?>> {
        val a = input.analysis
        val profile = a.conveyorProfile
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val start = a.summary.startTimeMs
        return listOf(
            listOf("Field", "Value"),
            listOf("Session ID", a.sessionId),
            listOf("Inspection Date", if (start > 0L) dateFmt.format(Date(start)) else ""),
            listOf("Building", input.context.buildingName),
            listOf("Floor", input.context.floorName),
            listOf("Drawing", input.context.drawingName.ifBlank { a.drawingId }),
            listOf("Conveyor Profile", profile.motionProfile.name),
            listOf("Nominal Speed", profile.nominalSpeedMPerMin ?: ""),
            listOf("Inspection Time", if (start > 0L) dateFmt.format(Date(start)) else ""),
            listOf("Duration (ms)", a.summary.durationMs),
            listOf("Distance (mm)", a.distance.totalDistanceMm),
            listOf("Coverage", maxOf(a.coverage.drawingCoverage, a.coverage.routeCoverage)),
            listOf("Validation Score", a.validationScore),
            listOf("Average Speed (mm/s)", a.speed.averageSpeedMmPerSec),
            listOf("Maximum Speed (mm/s)", a.speed.maximumSpeedMmPerSec),
            listOf("Maximum Shock", a.shock.maximumShock),
            listOf("Shock Count", a.shock.shockCount),
            listOf("Tracking Confidence", a.tracking.averageConfidence),
        )
    }

    private fun buildZoneSummary(input: Input): List<List<Any?>> {
        val header = listOf(
            "Zone Name",
            "Inspection Time",
            "Distance",
            "Coverage",
            "Average Speed",
            "Maximum Speed",
            "Average Shock",
            "Maximum Shock",
            "Shock Count",
            "Validation Score",
            "Warning Count",
            "Highest Severity",
        )
        val rows = ArrayList<List<Any?>>()
        rows += header
        val zoneHits = input.rules.triggered()
            .filter { !it.relatedZoneId.isNullOrBlank() }
            .groupBy { it.relatedZoneId!! }
        val analysisZones = input.analysis.zones.associateBy { it.zoneId }
        val summaries = input.rules.zoneSummaries
        val ids = (summaries.map { it.zoneId } + analysisZones.keys + zoneHits.keys).distinct()
        for (zoneId in ids) {
            val summary = summaries.find { it.zoneId == zoneId }
            val analysisZone = analysisZones[zoneId]
            val hits = zoneHits[zoneId].orEmpty()
            val highest = hits.minByOrNull { it.severity.ordinal }?.severity
            rows += listOf(
                summary?.zoneName ?: analysisZone?.zoneName ?: zoneId,
                analysisZone?.inspectionTimeMs ?: "",
                summary?.distanceMm ?: analysisZone?.distanceMm ?: "",
                summary?.coverage ?: analysisZone?.coverage ?: "",
                "", // Average Speed — not in Analysis ZoneStatistics (no recalculation)
                "", // Maximum Speed
                "", // Average Shock
                "", // Maximum Shock
                summary?.shockCount ?: analysisZone?.shockCount ?: 0,
                summary?.validationScore ?: "",
                hits.size,
                highest?.name ?: "",
            )
        }
        return rows
    }

    private fun buildRuleResult(input: Input): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        rows += listOf(
            "Rule ID",
            "Rule Version",
            "Severity",
            "Triggered",
            "Description",
            "Recommendation",
            "Related Zone",
        )
        for (hit in input.rules.hits) {
            rows += listOf(
                hit.ruleId,
                hit.ruleVersion,
                hit.severity.name,
                hit.triggered,
                hit.description,
                hit.recommendation.displayLabel(),
                hit.relatedZoneName ?: hit.relatedZoneId.orEmpty(),
            )
        }
        return rows
    }

    private fun buildEvents(input: Input): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        rows += listOf(
            "Timestamp",
            "Elapsed Time",
            "Building",
            "Floor",
            "Drawing",
            "Zone",
            "X Coordinate",
            "Y Coordinate",
            "Distance",
            "Current Speed",
            "Nominal Speed",
            "Speed Difference",
            "Shock",
            "Tracking Confidence",
            "Validation Score",
            "Rule Trigger",
        )
        val events = input.events.sortedBy { it.timestampNs }
        val startNs = events.firstOrNull()?.timestampNs ?: 0L
        val heat = input.heatPoints.sortedBy { it.timestampNs }
        val triggeredIds = input.rules.triggered().joinToString(";") { it.ruleId }
        val nominal = input.analysis.conveyorProfile.nominalSpeedMPerMin
        val speedDiff = input.analysis.speed.speedDifferenceMmPerSec
        val validation = input.analysis.validationScore
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        for (event in events) {
            val heatPt = nearestHeat(heat, event.timestampNs)
            val elapsedMs = if (startNs > 0L) {
                ((event.timestampNs - startNs) / 1_000_000L).coerceAtLeast(0L)
            } else {
                0L
            }
            val tsMs = event.timestampNs / 1_000_000L
            rows += listOf(
                if (tsMs > 0L) dateFmt.format(Date(tsMs)) else event.timestampNs.toString(),
                elapsedMs,
                input.context.buildingName,
                input.context.floorName,
                input.context.drawingName.ifBlank { event.drawingId },
                "", // Zone — not stored on event; no recalculation
                heatPt?.drawingX ?: "",
                heatPt?.drawingY ?: "",
                event.distanceMm,
                "", // Current Speed not persisted on event
                nominal ?: "",
                speedDiff,
                if (event.hasShock) event.shockStrength else 0f,
                event.trackingConfidence,
                validation,
                if (event.hasShock || event.trackingConfidence < 0.5f) triggeredIds else "",
            )
        }
        return rows
    }

    /** Lookup Heat Coordinate from Repository points — not HeatMap regeneration. */
    private fun nearestHeat(
        sortedHeat: List<DrawingHeatPoint>,
        timestampNs: Long,
    ): DrawingHeatPoint? {
        if (sortedHeat.isEmpty()) return null
        var best = sortedHeat.first()
        var bestDelta = abs(best.timestampNs - timestampNs)
        for (p in sortedHeat) {
            val d = abs(p.timestampNs - timestampNs)
            if (d < bestDelta) {
                best = p
                bestDelta = d
            }
        }
        return best
    }

    companion object {
        fun defaultFileName(nowMs: Long = System.currentTimeMillis()): String {
            val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            return "Inspection_${fmt.format(Date(nowMs))}.xlsx"
        }
    }
}
