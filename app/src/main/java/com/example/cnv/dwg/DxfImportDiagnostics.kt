package com.example.cnv.dwg

/**
 * In-memory DXF import diagnostics (STEP 20-9).
 * Not persisted to History / Inspection / Room.
 */
object DxfImportDiagnosticsStore {
    @Volatile
    var latest: DxfImportReport? = null
        private set

    fun publish(report: DxfImportReport) {
        latest = report
        DxfImportDiagnosticsLogger.log(report)
    }

    fun clear() {
        latest = null
    }
}

enum class DxfImportStatus {
    SUCCESS,
    WARNING,
    ERROR,
}

data class DxfImportSummary(
    val fileName: String,
    val dxfVersion: String,
    val drawingWidth: Double,
    val drawingHeight: Double,
    val layerCount: Int,
    val layerNames: List<String>,
    val entityCount: Int,
    val polylineCount: Int,
    val lineCount: Int,
    val arcCount: Int,
    val circleCount: Int,
    val textCount: Int,
    val blockCount: Int,
    val insertCount: Int,
    val geometryCount: Int,
    val routeCandidateCount: Int,
    val selectedConveyorLayer: String,
    val boundingBoxMinX: Double? = null,
    val boundingBoxMinY: Double? = null,
    val boundingBoxMaxX: Double? = null,
    val boundingBoxMaxY: Double? = null,
)

data class DxfImportValidation(
    val hasLayers: Boolean,
    val conveyorLayerExists: Boolean,
    val hasGeometry: Boolean,
    val hasRouteCandidates: Boolean,
    val calibrationGeometryOk: Boolean,
    val originSelectable: Boolean,
    val boundingBoxOk: Boolean,
    val emptyDrawing: Boolean,
    val warnings: List<String>,
    val errors: List<String>,
    val guidance: List<String>,
)

data class DxfImportReport(
    val status: DxfImportStatus,
    val summary: DxfImportSummary,
    val validation: DxfImportValidation,
    val parseError: String? = null,
) {
    fun userFacingMessage(): String = buildString {
        when (status) {
            DxfImportStatus.SUCCESS -> append("Import Success")
            DxfImportStatus.WARNING -> append("Import Success (Warnings)")
            DxfImportStatus.ERROR -> append("Import Error")
        }
        append("\n\nImport Summary")
        append("\nFile Name: ").append(summary.fileName)
        append("\nDXF Version: ").append(summary.dxfVersion)
        append("\nDrawing Size: ")
            .append("%.1f".format(summary.drawingWidth))
            .append(" x ")
            .append("%.1f".format(summary.drawingHeight))
        append("\nLayer Count: ").append(summary.layerCount)
        append("\nLayers: ").append(summary.layerNames.joinToString(", ").ifBlank { "(none)" })
        append("\nEntity Count: ").append(summary.entityCount)
        append("\nPolyline: ").append(summary.polylineCount)
        append("  Line: ").append(summary.lineCount)
        append("  Arc: ").append(summary.arcCount)
        append("  Circle: ").append(summary.circleCount)
        append("  Text: ").append(summary.textCount)
        append("\nBlock: ").append(summary.blockCount)
        append("  Insert: ").append(summary.insertCount)
        append("\nGeometry Count: ").append(summary.geometryCount)
        append("\nRoute Candidates: ").append(summary.routeCandidateCount)
        append("\nConveyor Layer: ").append(summary.selectedConveyorLayer)
        if (validation.warnings.isNotEmpty()) {
            append("\n\nWarnings")
            validation.warnings.forEach { append("\n- ").append(it) }
        }
        if (validation.errors.isNotEmpty()) {
            append("\n\nErrors")
            validation.errors.forEach { append("\n- ").append(it) }
        }
        if (validation.guidance.isNotEmpty()) {
            append("\n\nNext")
            validation.guidance.forEach { append("\n- ").append(it) }
        }
    }
}
