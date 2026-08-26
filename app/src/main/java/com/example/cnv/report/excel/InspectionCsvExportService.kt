package com.example.cnv.report.excel

import android.content.ContentResolver
import android.net.Uri
import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.imu.ShockUnits
import com.example.cnv.inspection.PersistedInspectionEvent
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.profile.InspectionProfileCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV export for Inspection Summary (STEP 20-18 / 20-20).
 * Repository / Analysis data only — no engine recalculation.
 */
class InspectionCsvExportService(
    private val catalog: FactoryCatalog,
) {

    data class Result(
        val success: Boolean,
        val fileUri: String? = null,
        val fileName: String? = null,
        val errorMessage: String? = null,
    )

    fun exportAsync(
        sessionId: String,
        drawingId: String,
        targetUri: Uri,
        contentResolver: ContentResolver,
        fileName: String = defaultFileName(sessionId),
        onDone: (Result) -> Unit,
    ) {
        InspectionDbGate.submit(
            block = {
                val r = exportSync(sessionId, drawingId, targetUri, contentResolver, fileName)
                println(
                    "LOG[STEP20-20][CSV] success=${r.success} file=${r.fileName} err=${r.errorMessage}",
                )
                r
            },
            onMain = onDone,
            onError = { e ->
                println("LOG[STEP20-20][CSV] success=false err=${e.message}")
                onDone(Result(success = false, errorMessage = e.message ?: "CSV export failed"))
            },
        )
    }

    fun exportSync(
        sessionId: String,
        drawingId: String,
        targetUri: Uri,
        contentResolver: ContentResolver,
        fileName: String,
    ): Result {
        val analysis = catalog.analysis.analyzeSync(sessionId, drawingId)
            ?: return Result(false, errorMessage = "Analysis Result unavailable")
        val persisted = catalog.inspections.loadSession(sessionId)
            ?: return Result(false, errorMessage = "Inspection Session unavailable")
        if (persisted.summary.drawingId != drawingId) {
            return Result(false, errorMessage = "Session / Drawing mismatch")
        }
        val profile = InspectionProfileCodec.decodeSnapshot(persisted.summary.inspectionProfileJson)
        val ctx = InspectionExportContext.build(catalog, drawingId, analysis, persisted.events, profile)
        val csv = buildCsv(ctx, analysis, persisted.events)
        contentResolver.openOutputStream(targetUri)?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: return Result(false, errorMessage = "Cannot open output stream")
        return Result(success = true, fileUri = targetUri.toString(), fileName = fileName)
    }

    companion object {
        fun defaultFileName(sessionId: String): String =
            "inspection_${sessionId.take(8)}.csv"

        fun buildCsv(
            ctx: InspectionExportContext,
            analysis: InspectionAnalysisResult,
            events: List<PersistedInspectionEvent>,
            shockClipCount: Int = events.count { it.hasShock && it.clipPath.isNotBlank() },
        ): String = buildString {
            fun row(k: String, v: Any?) {
                append(escape(k)).append(',').append(escape(v?.toString().orEmpty())).append('\n')
            }
            val origin = if (ctx.originSet) {
                "${ctx.originX ?: ""},${ctx.originY ?: ""}"
            } else {
                ""
            }
            appendLine("field,value")
            row("Inspection Summary", "P0")
            row("Timestamp", ctx.timestampLabel)
            row("Building", ctx.buildingName)
            row("Floor", ctx.floorName)
            row("Drawing", ctx.drawingName)
            row("Zone", ctx.zoneName)
            row("Inspection Time", ctx.inspectionTimeLabel)
            row("Route Length (mm)", ctx.routeLengthMm)
            row("Average Speed (mm/s)", analysis.speed.averageSpeedMmPerSec)
            row("Maximum Speed (mm/s)", analysis.speed.maximumSpeedMmPerSec)
            row("Average Shock (G)", analysis.shock.averageShock)
            row("Maximum Shock (G)", analysis.shock.maximumShock)
            row(
                "Critical Shocks",
                events.count { it.hasShock && com.example.cnv.imu.ShockUnits.isCriticalG(it.peakG.coerceAtLeast(it.shockStrength)) },
            )
            row("Shock Events", analysis.shock.shockCount)
            row("Shock Video Clips", shockClipCount)
            row("Threshold (G)", ctx.shockThreshold)
            row("Calibration(mmPerPixel)", ctx.mmPerPixel ?: "")
            row("Origin", origin)
            row("Origin Set", ctx.originSet)
            row("Session ID", analysis.sessionId)
            row("Duration (ms)", analysis.summary.durationMs)
            row("Distance (mm)", analysis.distance.totalDistanceMm)
            appendLine()
            appendLine(
                "Timestamp,Building,Floor,Drawing,Zone,Route Position," +
                    "World X,World Y,Shock(G),Peak(G),Average(G),Speed," +
                    "Calibration(mmPerPixel),Origin,Video Clip",
            )
            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val shockEvents = events.filter { it.hasShock }.sortedBy { it.timestampNs }
            for (e in shockEvents) {
                val ts = dateFmt.format(Date(e.timestampNs / 1_000_000L))
                val zone = e.zoneName.ifBlank { ctx.zoneName }
                val routePos = if (e.routePositionMm > 0f) {
                    e.routePositionMm.toString()
                } else {
                    e.routePosition
                }
                val speed = if (e.speedMmPerSec != 0f) {
                    e.speedMmPerSec
                } else {
                    analysis.speed.averageSpeedMmPerSec
                }
                append(escape(ts)).append(',')
                append(escape(ctx.buildingName)).append(',')
                append(escape(ctx.floorName)).append(',')
                append(escape(ctx.drawingName)).append(',')
                append(escape(zone)).append(',')
                append(escape(routePos)).append(',')
                append(e.worldX).append(',')
                append(e.worldY).append(',')
                append(e.shockStrength).append(',')
                append(e.peakG.takeIf { it > 0f } ?: e.shockStrength).append(',')
                append(e.movingAverageG).append(',')
                append(speed).append(',')
                append(ctx.mmPerPixel ?: "").append(',')
                append(escape(origin)).append(',')
                append(escape(if (e.clipPath.isNotBlank()) "yes" else "")).append('\n')
            }
        }

        private fun escape(raw: String): String {
            if (raw.contains(',') || raw.contains('"') || raw.contains('\n')) {
                return "\"${raw.replace("\"", "\"\"")}\""
            }
            return raw
        }
    }
}

/**
 * Shared export context for Excel Summary + CSV.
 */
data class InspectionExportContext(
    val buildingName: String,
    val floorName: String,
    val drawingName: String,
    val zoneName: String,
    val timestampLabel: String,
    val inspectionTimeLabel: String,
    val routeLengthMm: Float,
    val mmPerPixel: Float?,
    val originSet: Boolean,
    val originX: Float?,
    val originY: Float?,
    val shockThreshold: Float,
) {
    companion object {
        fun build(
            catalog: FactoryCatalog,
            drawingId: String,
            analysis: InspectionAnalysisResult,
            events: List<PersistedInspectionEvent>,
            profile: com.example.cnv.profile.InspectionProfileSnapshot,
        ): InspectionExportContext {
            val drawing = catalog.drawings.get(drawingId)
            val floor = drawing?.floorId?.let { catalog.floors.get(it) }
            val building = floor?.buildingId?.let { catalog.buildings.get(it) }
            val cal = catalog.calibrations.get(drawingId)
            val zones = catalog.zones.forDrawing(drawingId)
            val zoneName = zones.firstOrNull()?.name.orEmpty().ifBlank {
                analysis.zones.maxByOrNull { it.shockCount }?.zoneName.orEmpty()
            }
            val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val start = analysis.summary.startTimeMs
            val timeLabel = if (start > 0L) dateFmt.format(Date(start)) else ""
            val routeLen = catalog.routes.currentRoute()?.let { r ->
                r.segments.sumOf { it.lengthMm.toDouble() }.toFloat()
            } ?: analysis.distance.totalDistanceMm
            val thr = ShockUnits.asThresholdG(profile.sensor.minimumShockThreshold)
            return InspectionExportContext(
                buildingName = building?.name.orEmpty(),
                floorName = floor?.name.orEmpty(),
                drawingName = drawing?.name.orEmpty().ifBlank { drawingId },
                zoneName = zoneName,
                timestampLabel = timeLabel,
                inspectionTimeLabel = timeLabel,
                routeLengthMm = routeLen,
                mmPerPixel = cal?.mmPerPixel,
                originSet = drawing?.originSet == true,
                originX = drawing?.originX,
                originY = drawing?.originY,
                shockThreshold = thr,
            )
        }
    }
}
