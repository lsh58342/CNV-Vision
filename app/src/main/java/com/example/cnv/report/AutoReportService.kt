package com.example.cnv.report

import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.report.excel.ExcelArchiveEntry
import com.example.cnv.report.excel.InspectionCsvExportService
import com.example.cnv.report.excel.InspectionExcelExportService
import com.example.cnv.report.excel.InspectionExcelReportGenerator
import com.example.cnv.rule.InspectionRuleResult
import java.io.FileOutputStream

/**
 * Generates Excel / CSV / Maintenance exports automatically when an Inspection finishes.
 * Uses Analysis + Rule results already computed during [InspectionPipeline.stopSession].
 */
object AutoReportService {

    data class Result(
        val success: Boolean,
        val sessionId: String = "",
        val excelFileName: String? = null,
        val excelUri: String? = null,
        val csvFileName: String? = null,
        val jsonFileName: String? = null,
        val maintenanceGrade: String? = null,
        val overallStatus: String? = null,
        val shockClipCount: Int = 0,
        val errorMessage: String? = null,
    )

    fun generateSync(
        catalog: FactoryCatalog,
        sessionId: String,
        drawingId: String,
        analysis: InspectionAnalysisResult,
        rules: InspectionRuleResult,
        heatPoints: List<DrawingHeatPoint>,
    ): Result {
        if (!AutoReportSettingsStore.isEnabled()) {
            return Result(success = false, sessionId = sessionId, errorMessage = "Auto report disabled")
        }
        return runCatching {
            val persisted = catalog.inspections.loadSession(sessionId)
                ?: return Result(false, sessionId, errorMessage = "Session not found")
            if (persisted.summary.drawingId != drawingId) {
                return Result(false, sessionId, errorMessage = "Drawing mismatch")
            }

            val excelService = InspectionExcelExportService(catalog)
            val input = excelService.prepareInput(
                sessionId = sessionId,
                drawingId = drawingId,
                analysis = analysis,
                rules = rules,
                events = persisted.events,
                heatPoints = heatPoints,
                profileJson = persisted.summary.inspectionProfileJson,
                startTimeMs = persisted.summary.startTimeMs,
            ) ?: return Result(false, sessionId, errorMessage = "Report input unavailable")

            val excelFile = ReportStorage.excelFile(sessionId)
            excelFile.parentFile?.mkdirs()
            FileOutputStream(excelFile).use { out ->
                InspectionExcelReportGenerator().write(input, out)
                out.flush()
            }

            val csvFile = ReportStorage.csvFile(sessionId)
            val profile = com.example.cnv.profile.InspectionProfileCodec.decodeSnapshot(
                persisted.summary.inspectionProfileJson,
            )
            val csvCtx = com.example.cnv.report.excel.InspectionExportContext.build(
                catalog = catalog,
                drawingId = drawingId,
                analysis = analysis,
                events = persisted.events,
                profile = profile,
            )
            val clipCount = persisted.events.count { it.hasShock && it.clipPath.isNotBlank() }
            val mapInput = ReportMapBuilder.fromSessionData(
                routeSnapshotJson = persisted.summary.routeSnapshotJson,
                heatPoints = heatPoints,
                events = persisted.events,
            )
            ReportMapRenderer.renderToFile(mapInput, ReportStorage.criticalMapFile(sessionId))
            csvFile.writeText(
                InspectionCsvExportService.buildCsv(csvCtx, analysis, persisted.events, clipCount),
            )

            val maintenance = MaintenanceReportAssembler.assemble(
                analysis = analysis,
                rules = rules,
                buildingId = catalog.drawings.get(drawingId)?.floorId?.let { fid ->
                    catalog.floors.get(fid)?.buildingId
                },
                buildingName = csvCtx.buildingName,
                floorId = catalog.drawings.get(drawingId)?.floorId,
                floorName = csvCtx.floorName,
                drawingName = csvCtx.drawingName,
            )
            catalog.reports.cacheReport(maintenance)

            val jsonPayload = ReportExporter.export(maintenance, ReportExportFormat.JSON)
            ReportStorage.maintenanceJsonFile(sessionId).writeText(jsonPayload.content)

            val csvPayload = ReportExporter.export(maintenance, ReportExportFormat.CSV)
            ReportStorage.maintenanceCsvFile(sessionId).writeText(csvPayload.content)

            val textPayload = ReportExporter.export(maintenance, ReportExportFormat.PDF)
            ReportStorage.maintenanceTextFile(sessionId).writeText(textPayload.content)

            val excelUri = ReportStorage.fileUri(excelFile).toString()
            val excelEntry = ExcelArchiveEntry(
                sessionId = sessionId,
                drawingId = drawingId,
                fileUri = excelUri,
                fileName = excelFile.name,
            )
            catalog.excelArchives.putAndPersist(excelEntry, catalog.inspections.underlying())

            println(
                "LOG[AutoReport][OK] session=$sessionId excel=${excelFile.name} " +
                    "csv=${csvFile.name} json=${jsonPayload.fileName} " +
                    "grade=${maintenance.maintenanceSummary.inspectionGrade} " +
                    "status=${maintenance.maintenanceSummary.overallStatus} clips=$clipCount",
            )
            Result(
                success = true,
                sessionId = sessionId,
                excelFileName = excelFile.name,
                excelUri = excelUri,
                csvFileName = csvFile.name,
                jsonFileName = jsonPayload.fileName,
                maintenanceGrade = maintenance.maintenanceSummary.inspectionGrade.name,
                overallStatus = maintenance.maintenanceSummary.overallStatus.name,
                shockClipCount = clipCount,
            )
        }.getOrElse {
            println("LOG[AutoReport][FAIL] session=$sessionId err=${it.message}")
            Result(success = false, sessionId = sessionId, errorMessage = it.message ?: "Auto report failed")
        }
    }
}
