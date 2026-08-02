package com.example.cnv.report.excel

import android.content.ContentResolver
import android.net.Uri
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.inspection.db.InspectionDbGate

/**
 * Orchestrates Excel export on a background thread (STEP 19-1).
 * Reads Analysis / Rule / Inspection Event / HeatMap repositories only.
 */
class InspectionExcelExportService(
    private val catalog: FactoryCatalog,
    private val generator: InspectionExcelReportGenerator = InspectionExcelReportGenerator(),
) {

    data class Result(
        val success: Boolean,
        val fileUri: String? = null,
        val fileName: String? = null,
        val errorMessage: String? = null,
    )

    /**
     * Build workbook off main thread, write to [targetUri], archive Session ↔ path.
     */
    fun exportAsync(
        sessionId: String,
        drawingId: String,
        targetUri: Uri,
        contentResolver: ContentResolver,
        fileName: String = InspectionExcelReportGenerator.defaultFileName(),
        onDone: (Result) -> Unit,
    ) {
        InspectionDbGate.submit(
            block = {
                exportSync(sessionId, drawingId, targetUri, contentResolver, fileName)
            },
            onMain = onDone,
            onError = { e ->
                onDone(Result(success = false, errorMessage = e.message ?: "Excel export failed"))
            },
        )
    }

    /** Background-thread only. */
    fun exportSync(
        sessionId: String,
        drawingId: String,
        targetUri: Uri,
        contentResolver: ContentResolver,
        fileName: String,
    ): Result {
        val analysis = catalog.analysis.analyzeSync(sessionId, drawingId)
            ?: return Result(false, errorMessage = "Analysis Result unavailable")
        val rules = catalog.rules.evaluateSync(sessionId, drawingId)
            ?: return Result(false, errorMessage = "Rule Result unavailable")

        val persisted = catalog.inspections.loadSession(sessionId)
            ?: return Result(false, errorMessage = "Inspection Session unavailable")
        if (persisted.summary.drawingId != drawingId) {
            return Result(false, errorMessage = "Session / Drawing mismatch")
        }

        val heatPoints = catalog.heatMaps.loadHeatPointsForSession(drawingId, sessionId)
        val profileSnapshot = com.example.cnv.profile.InspectionProfileCodec.decodeSnapshot(
            persisted.summary.inspectionProfileJson,
        )
        val input = InspectionExcelReportGenerator.Input(
            analysis = analysis,
            rules = rules,
            events = persisted.events,
            heatPoints = heatPoints,
            profileSnapshot = profileSnapshot,
            context = buildContext(drawingId),
        )

        contentResolver.openOutputStream(targetUri)?.use { out ->
            generator.write(input, out)
            out.flush()
        } ?: return Result(false, errorMessage = "Cannot open output stream")

        catalog.excelArchives.put(
            ExcelArchiveEntry(
                sessionId = sessionId,
                drawingId = drawingId,
                fileUri = targetUri.toString(),
                fileName = fileName,
            ),
        )
        return Result(
            success = true,
            fileUri = targetUri.toString(),
            fileName = fileName,
        )
    }

    private fun buildContext(drawingId: String): InspectionExcelReportGenerator.Context {
        val current = CurrentContext.get()
        val drawing = catalog.drawings.get(drawingId) ?: catalog.drawings.current(current)
        val floor = drawing?.floorId?.let { catalog.floors.get(it) }
        val building = floor?.buildingId?.let { catalog.buildings.get(it) }
            ?: current.buildingId?.let { catalog.buildings.get(it) }
        return InspectionExcelReportGenerator.Context(
            buildingName = building?.name.orEmpty(),
            floorName = floor?.name.orEmpty(),
            drawingName = drawing?.name.orEmpty(),
        )
    }
}
