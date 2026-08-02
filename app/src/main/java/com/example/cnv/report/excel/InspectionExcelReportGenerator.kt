package com.example.cnv.report.excel

import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.inspection.PersistedInspectionEvent
import com.example.cnv.profile.InspectionProfileSnapshot
import com.example.cnv.rule.InspectionRuleResult
import com.example.cnv.rule.RuleSeverity
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Advanced Inspection Excel workbook (STEP 19-2).
 * Repository data only — no Analysis / Rule / HeatMap recalculation.
 */
class InspectionExcelReportGenerator {

    data class Context(
        val buildingName: String = "",
        val floorName: String = "",
        val drawingName: String = "",
        val zoneName: String = "",
        val timestampLabel: String = "",
        val inspectionTimeLabel: String = "",
        val routeLengthMm: Float = 0f,
        val mmPerPixel: Float? = null,
        val originSet: Boolean = false,
        val originX: Float? = null,
        val originY: Float? = null,
        val shockThreshold: Float = 0f,
    )

    data class Input(
        val analysis: InspectionAnalysisResult,
        val rules: InspectionRuleResult,
        val events: List<PersistedInspectionEvent>,
        val heatPoints: List<DrawingHeatPoint>,
        val profileSnapshot: InspectionProfileSnapshot = InspectionProfileSnapshot.empty(),
        val context: Context = Context(),
    )

    fun write(input: Input, output: OutputStream) {
        val rankings = ZoneExcelAnalytics.buildRankings(input.analysis, input.rules, input.events)
        val top = ZoneExcelAnalytics.topWorst(rankings, 10)
        val timeFmt = input.profileSnapshot.export.timeFormat.ifBlank {
            "yyyy-MM-dd HH:mm:ss.SSS"
        }

        val writer = XlsxWorkbookWriter()
        val dashboard = buildDashboard(input, rankings, top)
        writer.addStyledSheet("Dashboard", dashboard)
        writer.addSheet("Summary", buildSummary(input))
        writer.addStyledSheet("Zone Analytics", buildZoneAnalytics(rankings))
        writer.addStyledSheet("Rule Result", buildRuleResult(input))
        writer.addSheet("Inspection Timeline", buildTimeline(input, timeFmt))
        writer.addSheet("Inspection Events", buildEvents(input, timeFmt))
        writer.addSheet("Inspection Profile", buildProfileSheet(input))

        val zoneEnd = rankings.size + 1
        if (rankings.isNotEmpty()) {
            // Zone Analytics columns (0-based): Name=1, Count=3, Max=4, Avg=5, Density=9, Coverage=10
            writer.addBarChart(
                title = "Zone Shock Count",
                sheetName = "Zone Analytics",
                categoryCol = 1,
                valueCols = listOf("Shock Count" to 3),
                dataStartRow = 2,
                dataEndRow = zoneEnd,
            )
            writer.addBarChart(
                title = "Zone Maximum Shock",
                sheetName = "Zone Analytics",
                categoryCol = 1,
                valueCols = listOf("Max Shock" to 4),
                dataStartRow = 2,
                dataEndRow = zoneEnd,
            )
            writer.addBarChart(
                title = "Zone Average Shock",
                sheetName = "Zone Analytics",
                categoryCol = 1,
                valueCols = listOf("Avg Shock" to 5),
                dataStartRow = 2,
                dataEndRow = zoneEnd,
            )
            writer.addBarChart(
                title = "Shock Density",
                sheetName = "Zone Analytics",
                categoryCol = 1,
                valueCols = listOf("Shock Density" to 9),
                dataStartRow = 2,
                dataEndRow = zoneEnd,
            )
            writer.addBarChart(
                title = "Coverage",
                sheetName = "Zone Analytics",
                categoryCol = 1,
                valueCols = listOf("Coverage" to 10),
                dataStartRow = 2,
                dataEndRow = zoneEnd,
            )
        }
        val chartStart = dashboard.indexOfFirst { row ->
            row.firstOrNull()?.value?.toString() == "Chart Metric"
        }
        if (chartStart >= 0) {
            writer.addBarChart(
                title = "Speed Difference",
                sheetName = "Dashboard",
                categoryCol = 0,
                valueCols = listOf("Value" to 1),
                dataStartRow = chartStart + 2,
                dataEndRow = chartStart + 2,
            )
        }
        writer.write(output)
    }

    private fun cell(
        value: Any?,
        style: XlsxWorkbookWriter.CellStyle = XlsxWorkbookWriter.CellStyle.NORMAL,
    ) = XlsxWorkbookWriter.Cell(value, style)

    private fun buildDashboard(
        input: Input,
        rankings: List<ZoneExcelAnalytics.ZoneRankRow>,
        top: List<ZoneExcelAnalytics.ZoneRankRow>,
    ): List<List<XlsxWorkbookWriter.Cell>> {
        val a = input.analysis
        val triggered = input.rules.triggered()
        val H = XlsxWorkbookWriter.CellStyle.HEADER
        val rows = ArrayList<List<XlsxWorkbookWriter.Cell>>()

        rows.add(listOf(cell("Inspection Dashboard", H), cell("Excel v${input.profileSnapshot.export.excelVersion}", H)))
        rows.add(listOf(cell("Session ID"), cell(a.sessionId)))
        rows.add(listOf(cell("Drawing"), cell(input.context.drawingName.ifBlank { a.drawingId })))
        rows.add(
            listOf(
                cell("Coverage"),
                cell(
                    maxOf(a.coverage.drawingCoverage, a.coverage.routeCoverage),
                    coverageStyle(maxOf(a.coverage.drawingCoverage, a.coverage.routeCoverage)),
                ),
            ),
        )
        rows.add(listOf(cell("Validation Score"), cell(a.validationScore)))
        rows.add(listOf(cell("Distance (mm)"), cell(a.distance.totalDistanceMm)))
        rows.add(listOf(cell("Shock Count"), cell(a.shock.shockCount)))
        rows.add(listOf(cell("Max Shock"), cell(a.shock.maximumShock, shockStyle(a.shock.maximumShock))))
        rows.add(listOf(cell("Avg Speed (mm/s)"), cell(a.speed.averageSpeedMmPerSec)))
        rows.add(
            listOf(
                cell("Speed Difference"),
                cell(a.speed.speedDifferenceMmPerSec, speedDiffStyle(a.speed.speedDifferenceMmPerSec)),
            ),
        )
        rows.add(listOf(cell("Tracking Confidence"), cell(a.tracking.averageConfidence)))
        rows.add(
            listOf(
                cell("Tracking Loss"),
                cell(
                    a.tracking.trackingLossCount,
                    if (a.tracking.trackingLossCount > 0) XlsxWorkbookWriter.CellStyle.WARNING
                    else XlsxWorkbookWriter.CellStyle.NORMAL,
                ),
            ),
        )
        rows.add(listOf(cell("Triggered Rules"), cell(triggered.size)))
        rows.add(
            listOf(
                cell("Critical Rules"),
                cell(
                    triggered.count { it.severity == RuleSeverity.CRITICAL },
                    XlsxWorkbookWriter.CellStyle.CRITICAL,
                ),
            ),
        )
        rows.add(
            listOf(
                cell("High Rules"),
                cell(
                    triggered.count { it.severity == RuleSeverity.HIGH },
                    XlsxWorkbookWriter.CellStyle.HIGH,
                ),
            ),
        )
        rows.add(emptyList())
        rows.add(
            listOf(
                cell("Top 10 Worst Zones", H),
                cell("Priority", H),
                cell("Severity", H),
                cell("Max Shock", H),
                cell("Rules", H),
            ),
        )
        for (z in top) {
            rows.add(
                listOf(
                    cell(z.zoneName),
                    cell(z.priorityScore),
                    cell(z.highestSeverity?.name.orEmpty(), severityStyle(z.highestSeverity)),
                    cell(z.shock.maximum, shockStyle(z.shock.maximum)),
                    cell(z.triggeredRuleCount),
                ),
            )
        }
        rows.add(emptyList())
        rows.add(listOf(cell("Zone Ranking (all)", H)))
        rows.add(
            listOf(
                cell("Rank", H),
                cell("Zone", H),
                cell("Priority", H),
                cell("Coverage", H),
                cell("Validation", H),
            ),
        )
        for (z in rankings) {
            rows.add(
                listOf(
                    cell(z.rank),
                    cell(z.zoneName),
                    cell(z.priorityScore),
                    cell(z.coverage, coverageStyle(z.coverage)),
                    cell(z.validationScore),
                ),
            )
        }
        rows.add(emptyList())
        rows.add(listOf(cell("Chart Metric", H), cell("Value", H)))
        rows.add(
            listOf(
                cell("Speed Difference"),
                cell(a.speed.speedDifferenceMmPerSec, speedDiffStyle(a.speed.speedDifferenceMmPerSec)),
            ),
        )
        return rows
    }

    private fun buildSummary(input: Input): List<List<Any?>> {
        val a = input.analysis
        val profile = a.conveyorProfile
        val c = input.context
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val start = a.summary.startTimeMs
        val startLabel = if (start > 0L) dateFmt.format(Date(start)) else ""
        return listOf(
            listOf("Field", "Value"),
            listOf("Inspection Summary", "P0"),
            listOf("Session ID", a.sessionId),
            listOf("Timestamp", c.timestampLabel.ifBlank { startLabel }),
            listOf("Building", c.buildingName),
            listOf("Floor", c.floorName),
            listOf("Drawing", c.drawingName.ifBlank { a.drawingId }),
            listOf("Zone", c.zoneName),
            listOf("Inspection Time", c.inspectionTimeLabel.ifBlank { startLabel }),
            listOf("Inspection Date", startLabel),
            listOf("Duration (ms)", a.summary.durationMs),
            listOf("Route Length (mm)", c.routeLengthMm.takeIf { it > 0f } ?: a.distance.totalDistanceMm),
            listOf("Distance (mm)", a.distance.totalDistanceMm),
            listOf("Coverage", maxOf(a.coverage.drawingCoverage, a.coverage.routeCoverage)),
            listOf("Validation Score", a.validationScore),
            listOf("Average Speed (mm/s)", a.speed.averageSpeedMmPerSec),
            listOf("Maximum Speed (mm/s)", a.speed.maximumSpeedMmPerSec),
            listOf("Minimum Speed (mm/s)", a.speed.minimumSpeedMmPerSec),
            listOf("Average Shock (G)", a.shock.averageShock),
            listOf("Maximum Shock (G)", a.shock.maximumShock),
            listOf("Shock Events", a.shock.shockCount),
            listOf("Threshold (G)", c.shockThreshold),
            listOf("Calibration(mmPerPixel)", c.mmPerPixel ?: ""),
            listOf("Origin", if (c.originSet) "${c.originX ?: ""},${c.originY ?: ""}" else ""),
            listOf("Origin Set", c.originSet),
            listOf("Origin X", c.originX ?: ""),
            listOf("Origin Y", c.originY ?: ""),
            listOf("Conveyor Profile", profile.motionProfile.name),
            listOf("Nominal Speed", profile.nominalSpeedMPerMin ?: ""),
            listOf("Speed Difference", a.speed.speedDifferenceMmPerSec),
            listOf("Tracking Confidence", a.tracking.averageConfidence),
            listOf("Min Tracking Confidence", a.tracking.minimumConfidence),
            listOf("Tracking Loss Count", a.tracking.trackingLossCount),
        )
    }

    private fun buildZoneAnalytics(
        rankings: List<ZoneExcelAnalytics.ZoneRankRow>,
    ): List<List<XlsxWorkbookWriter.Cell>> {
        val H = XlsxWorkbookWriter.CellStyle.HEADER
        val rows = ArrayList<List<XlsxWorkbookWriter.Cell>>()
        rows.add(
            listOf(
                cell("Rank", H),
                cell("Zone Name", H),
                cell("Zone Length (mm)", H),
                cell("Shock Count", H),
                cell("Maximum Shock", H),
                cell("Average Shock", H),
                cell("Minimum Shock", H),
                cell("95 Percentile Shock", H),
                cell("Shock StdDev", H),
                cell("Shock Density", H),
                cell("Coverage", H),
                cell("Validation Score", H),
                cell("Highest Severity", H),
                cell("Triggered Rule Count", H),
                cell("Recommendation", H),
                cell("Median Shock", H),
            ),
        )
        for (z in rankings) {
            rows.add(
                listOf(
                    cell(z.rank),
                    cell(z.zoneName),
                    cell(z.zoneLengthMm),
                    cell(z.shock.shockCount),
                    cell(z.shock.maximum, shockStyle(z.shock.maximum)),
                    cell(z.shock.average),
                    cell(z.shock.minimum),
                    cell(z.shock.p95),
                    cell(z.shock.stdDev),
                    cell(z.shock.densityPerMeter),
                    cell(z.coverage, coverageStyle(z.coverage)),
                    cell(z.validationScore),
                    cell(z.highestSeverity?.name.orEmpty(), severityStyle(z.highestSeverity)),
                    cell(z.triggeredRuleCount),
                    cell(z.recommendation?.displayLabel().orEmpty()),
                    cell(z.shock.median),
                ),
            )
        }
        return rows
    }

    private fun buildRuleResult(input: Input): List<List<XlsxWorkbookWriter.Cell>> {
        val H = XlsxWorkbookWriter.CellStyle.HEADER
        val rows = ArrayList<List<XlsxWorkbookWriter.Cell>>()
        rows.add(
            listOf(
                cell("Rule ID", H),
                cell("Rule Version", H),
                cell("Severity", H),
                cell("Triggered", H),
                cell("Description", H),
                cell("Recommendation", H),
                cell("Related Zone", H),
            ),
        )
        for (hit in input.rules.hits) {
            rows.add(
                listOf(
                    cell(hit.ruleId),
                    cell(hit.ruleVersion),
                    cell(hit.severity.name, severityStyle(hit.severity)),
                    cell(hit.triggered),
                    cell(hit.description),
                    cell(hit.recommendation.displayLabel()),
                    cell(hit.relatedZoneName ?: hit.relatedZoneId.orEmpty()),
                ),
            )
        }
        return rows
    }

    private fun buildTimeline(
        input: Input,
        timeFmtPattern: String,
    ): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        rows.add(
            listOf(
                "Timestamp", "Elapsed Time", "Current Zone", "Distance",
                "Current Speed", "Shock", "Tracking Confidence", "Rule Trigger",
            ),
        )
        val events = input.events.sortedBy { it.timestampNs }
        val startNs = events.firstOrNull()?.timestampNs ?: 0L
        val triggered = input.rules.triggered().joinToString(";") { it.ruleId }
        val dateFmt = runCatching { SimpleDateFormat(timeFmtPattern, Locale.US) }
            .getOrElse { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }
        val zoneRanges = zoneDistanceRanges(input.analysis)
        var prevDist: Float? = null
        var prevTs: Long? = null
        for (e in events) {
            val elapsed = if (startNs > 0L) ((e.timestampNs - startNs) / 1_000_000L) else 0L
            val zone = zoneForDistance(e.distanceMm, zoneRanges)
            val speed: Any = if (prevDist != null && prevTs != null && e.timestampNs > prevTs) {
                val dt = (e.timestampNs - prevTs) / 1_000_000_000.0
                if (dt > 0) ((e.distanceMm - prevDist) / dt).toFloat() else ""
            } else {
                ""
            }
            rows.add(
                listOf(
                    dateFmt.format(Date(e.timestampNs / 1_000_000L)),
                    elapsed,
                    zone,
                    e.distanceMm,
                    speed,
                    if (e.hasShock) e.shockStrength else 0f,
                    e.trackingConfidence,
                    if (e.hasShock) triggered else "",
                ),
            )
            prevDist = e.distanceMm
            prevTs = e.timestampNs
        }
        return rows
    }

    private fun buildEvents(
        input: Input,
        timeFmtPattern: String,
    ): List<List<Any?>> {
        val rows = ArrayList<List<Any?>>()
        rows.add(
            listOf(
                "Timestamp", "Building", "Floor", "Drawing", "Zone",
                "Route Position", "World X", "World Y",
                "Shock(G)", "Peak(G)", "Average(G)", "Speed",
                "Calibration(mmPerPixel)", "Origin",
            ),
        )
        val events = input.events.filter { it.hasShock }.sortedBy { it.timestampNs }
        val dateFmt = runCatching { SimpleDateFormat(timeFmtPattern, Locale.US) }
            .getOrElse { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }
        val origin = if (input.context.originSet) {
            "${input.context.originX ?: ""},${input.context.originY ?: ""}"
        } else {
            ""
        }
        val cal = input.context.mmPerPixel ?: ""
        for (e in events) {
            val zone = e.zoneName.ifBlank { input.context.zoneName }
            val routePos: Any = if (e.routePositionMm > 0f) e.routePositionMm else e.routePosition
            val speed: Any = if (e.speedMmPerSec != 0f) {
                e.speedMmPerSec
            } else {
                input.analysis.speed.averageSpeedMmPerSec
            }
            val wx: Any = if (e.worldX != 0f || e.worldY != 0f) {
                e.worldX
            } else {
                nearestHeat(input.heatPoints, e.timestampNs)?.drawingX ?: ""
            }
            val wy: Any = if (e.worldX != 0f || e.worldY != 0f) {
                e.worldY
            } else {
                nearestHeat(input.heatPoints, e.timestampNs)?.drawingY ?: ""
            }
            rows.add(
                listOf(
                    dateFmt.format(Date(e.timestampNs / 1_000_000L)),
                    input.context.buildingName,
                    input.context.floorName,
                    input.context.drawingName,
                    zone,
                    routePos,
                    wx,
                    wy,
                    e.shockStrength,
                    e.peakG.takeIf { it > 0f } ?: e.shockStrength,
                    e.movingAverageG,
                    speed,
                    cal,
                    origin,
                ),
            )
        }
        return rows
    }

    private fun buildProfileSheet(input: Input): List<List<Any?>> {
        val snap = input.profileSnapshot
        val c = snap.conveyor
        val s = snap.sensor
        val e = snap.export
        val rows = ArrayList<List<Any?>>()
        rows.add(listOf("Section", "Field", "Value"))
        rows.add(listOf("Conveyor Profile", "Nominal Speed", c.nominalSpeedMPerMin ?: ""))
        rows.add(listOf("Conveyor Profile", "Speed Tolerance %", c.speedTolerancePercent))
        rows.add(listOf("Conveyor Profile", "Direction", c.direction.name))
        rows.add(listOf("Conveyor Profile", "Expected FPS", c.expectedFps))
        rows.add(listOf("Conveyor Profile", "Motion Profile", c.motionProfile.name))
        rows.add(listOf("Sensor Profile", "Gravity Filter Alpha", s.gravityFilterAlpha))
        rows.add(listOf("Sensor Profile", "High Pass Alpha", s.highPassAlpha))
        rows.add(listOf("Sensor Profile", "Minimum Shock Threshold", s.minimumShockThreshold))
        rows.add(listOf("Sensor Profile", "Peak Interval (ns)", s.peakIntervalNs))
        rows.add(listOf("Sensor Profile", "Moving Average Window", s.movingAverageWindow))
        rows.add(listOf("Sensor Profile", "Tracking Confidence Threshold", s.trackingConfidenceThreshold))
        rows.add(listOf("Rule Profile", "Catalog Version", snap.rule.catalogVersion))
        for (entry in snap.rule.entries) {
            rows.add(
                listOf(
                    "Rule Profile",
                    entry.ruleId,
                    "enabled=${entry.enabled};v=${entry.ruleVersion};thr=${entry.thresholdOverride ?: "-"};sev=${entry.severityOverride?.name ?: "-"}",
                ),
            )
        }
        rows.add(listOf("Export Profile", "Excel Version", e.excelVersion))
        rows.add(listOf("Export Profile", "Export Option", e.exportOption))
        rows.add(listOf("Export Profile", "Time Format", e.timeFormat))
        rows.add(listOf("Export Profile", "Coordinate Format", e.coordinateFormat))
        rows.add(listOf("Snapshot", "Captured At Ms", snap.capturedAtMs))
        return rows
    }

    /** Analysis zone order (route distance buckets) — not priority rank order. */
    private fun zoneDistanceRanges(
        analysis: InspectionAnalysisResult,
    ): List<Triple<String, Float, Float>> {
        var cursor = 0f
        return analysis.zones.map { z ->
            val start = cursor
            cursor += z.distanceMm.coerceAtLeast(0f)
            Triple(z.zoneName, start, cursor)
        }
    }

    private fun zoneForDistance(
        distanceMm: Float,
        ranges: List<Triple<String, Float, Float>>,
    ): String {
        if (ranges.isEmpty()) return ""
        return ranges.firstOrNull { distanceMm >= it.second && distanceMm <= it.third }?.first
            ?: ranges.last().first
    }

    private fun nearestHeat(sorted: List<DrawingHeatPoint>, ts: Long): DrawingHeatPoint? {
        if (sorted.isEmpty()) return null
        var best = sorted.first()
        var bestD = abs(best.timestampNs - ts)
        for (p in sorted) {
            val d = abs(p.timestampNs - ts)
            if (d < bestD) {
                best = p
                bestD = d
            }
        }
        return best
    }

    private fun severityStyle(sev: RuleSeverity?): XlsxWorkbookWriter.CellStyle = when (sev) {
        RuleSeverity.CRITICAL -> XlsxWorkbookWriter.CellStyle.CRITICAL
        RuleSeverity.HIGH -> XlsxWorkbookWriter.CellStyle.HIGH
        RuleSeverity.MEDIUM -> XlsxWorkbookWriter.CellStyle.WARNING
        else -> XlsxWorkbookWriter.CellStyle.NORMAL
    }

    private fun shockStyle(max: Float): XlsxWorkbookWriter.CellStyle = when {
        max >= 12f -> XlsxWorkbookWriter.CellStyle.CRITICAL
        max >= 6f -> XlsxWorkbookWriter.CellStyle.HIGH
        max > 0f -> XlsxWorkbookWriter.CellStyle.WARNING
        else -> XlsxWorkbookWriter.CellStyle.NORMAL
    }

    private fun coverageStyle(coverage: Float): XlsxWorkbookWriter.CellStyle = when {
        coverage < 0.5f -> XlsxWorkbookWriter.CellStyle.WARNING
        coverage >= 0.85f -> XlsxWorkbookWriter.CellStyle.GOOD
        else -> XlsxWorkbookWriter.CellStyle.NORMAL
    }

    private fun speedDiffStyle(diff: Float): XlsxWorkbookWriter.CellStyle =
        if (abs(diff) > 50f) XlsxWorkbookWriter.CellStyle.WARNING
        else XlsxWorkbookWriter.CellStyle.NORMAL

    companion object {
        fun defaultFileName(nowMs: Long = System.currentTimeMillis()): String {
            val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            return "Inspection_${fmt.format(Date(nowMs))}.xlsx"
        }
    }
}
