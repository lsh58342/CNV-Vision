package com.example.cnv.dwg

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Builds [DxfImportReport] after CAD open without changing GeometryExtractor / RouteExtractor.
 */
object DxfImportAnalyzer {

    fun analyze(
        sourcePath: String,
        conveyorLayer: String = DWGConfig.DEFAULT_LAYER_FILTER,
    ): DxfImportReport {
        val fileName = sourcePath.substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { "unknown.dxf" }
        val dxfVersion = peekDxfVersion(sourcePath)

        return try {
            val reader = CadReaderFactory.create(sourcePath)
            val importer = DWGImporter(reader = reader)
            val imported = importer.importFrom(sourcePath, layerName = conveyorLayer)
            val geometry = imported.geometry
            val bagCounts = EntityCounts.fromGeometry(geometry)
            val dxfStats = (reader as? DxfReader)?.lastStats
            val bbox = computeBoundingBox(geometry)

            val summary = DxfImportSummary(
                fileName = imported.fileName.ifBlank { fileName },
                dxfVersion = dxfVersion,
                drawingWidth = bbox?.width ?: 0.0,
                drawingHeight = bbox?.height ?: 0.0,
                layerCount = geometry.layers.size,
                layerNames = geometry.layerNames(),
                entityCount = bagCounts.total,
                polylineCount = bagCounts.polylines,
                lineCount = bagCounts.lines,
                arcCount = bagCounts.arcs,
                circleCount = bagCounts.circles,
                textCount = bagCounts.texts,
                blockCount = dxfStats?.blockDefinitionCount
                    ?: geometry.blocks.size,
                insertCount = dxfStats?.insertCount ?: 0,
                geometryCount = bagCounts.total,
                routeCandidateCount = imported.routeCandidateCount,
                selectedConveyorLayer = conveyorLayer,
                boundingBoxMinX = bbox?.minX,
                boundingBoxMinY = bbox?.minY,
                boundingBoxMaxX = bbox?.maxX,
                boundingBoxMaxY = bbox?.maxY,
            )

            val validation = validate(summary, dxfStats)
            val status = when {
                validation.errors.isNotEmpty() -> DxfImportStatus.ERROR
                validation.warnings.isNotEmpty() -> DxfImportStatus.WARNING
                else -> DxfImportStatus.SUCCESS
            }
            DxfImportReport(status = status, summary = summary, validation = validation)
                .also { DxfImportDiagnosticsStore.publish(it) }
        } catch (t: Throwable) {
            val emptySummary = DxfImportSummary(
                fileName = fileName,
                dxfVersion = dxfVersion,
                drawingWidth = 0.0,
                drawingHeight = 0.0,
                layerCount = 0,
                layerNames = emptyList(),
                entityCount = 0,
                polylineCount = 0,
                lineCount = 0,
                arcCount = 0,
                circleCount = 0,
                textCount = 0,
                blockCount = 0,
                insertCount = 0,
                geometryCount = 0,
                routeCandidateCount = 0,
                selectedConveyorLayer = conveyorLayer,
            )
            val errors = listOf(
                "DXF Parsing 실패",
                t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName,
            )
            val validation = DxfImportValidation(
                hasLayers = false,
                conveyorLayerExists = false,
                hasGeometry = false,
                hasRouteCandidates = false,
                calibrationGeometryOk = false,
                originSelectable = false,
                boundingBoxOk = false,
                emptyDrawing = true,
                warnings = emptyList(),
                errors = errors,
                guidance = listOf(
                    "DXF Version을 확인하십시오.",
                    "파일이 손상되지 않았는지 확인하십시오.",
                ),
            )
            DxfImportReport(
                status = DxfImportStatus.ERROR,
                summary = emptySummary,
                validation = validation,
                parseError = t.message,
            ).also { DxfImportDiagnosticsStore.publish(it) }
        }
    }

    private fun validate(
        summary: DxfImportSummary,
        dxfStats: DxfReader.CollectStats?,
    ): DxfImportValidation {
        val warnings = ArrayList<String>()
        val errors = ArrayList<String>()
        val guidance = ArrayList<String>()

        val hasLayers = summary.layerCount > 0
        val conveyorLayerExists = summary.layerNames.any {
            it.equals(summary.selectedConveyorLayer, ignoreCase = true)
        }
        val hasGeometry = summary.geometryCount > 0
        val hasRouteCandidates = summary.routeCandidateCount > 0
        val bboxOk = summary.boundingBoxMinX != null &&
            summary.drawingWidth >= 0.0 &&
            summary.drawingHeight >= 0.0 &&
            (summary.drawingWidth > 0.0 || summary.drawingHeight > 0.0 || hasGeometry)
        val calibrationGeometryOk = hasGeometry &&
            (summary.lineCount + summary.polylineCount + summary.arcCount + summary.circleCount) > 0
        val originSelectable = hasGeometry && bboxOk
        val emptyDrawing = !hasGeometry

        val blocksOnly = (dxfStats?.blockDefinitionCount ?: summary.blockCount) > 0 &&
            (dxfStats?.insertCount ?: summary.insertCount) == 0 &&
            summary.geometryCount == 0

        if (!hasLayers) {
            errors.add("Layer 없음")
            guidance.add("DXF에 Layer Table이 있는지 확인하십시오.")
        }
        if (!conveyorLayerExists) {
            warnings.add("Conveyor Layer 없음")
            guidance.add("Conveyor Layer를 선택하십시오.")
        }
        if (emptyDrawing) {
            errors.add("Geometry 없음")
            guidance.add("Geometry가 존재하지 않습니다.")
        }
        if (blocksOnly) {
            warnings.add("BLOCK만 존재")
            guidance.add("BLOCK만 존재합니다. INSERT가 포함된 DXF를 사용하십시오.")
        }
        if (!hasRouteCandidates) {
            warnings.add("Route Candidate 0개")
            if (conveyorLayerExists) {
                guidance.add("선택한 Layer에 충분한 Polyline/Line이 있는지 확인하십시오.")
            } else {
                guidance.add("Conveyor Layer를 선택하십시오.")
            }
        }
        if (!calibrationGeometryOk && hasGeometry) {
            warnings.add("Calibration 가능한 Geometry 부족")
        }
        if (!originSelectable && hasGeometry) {
            warnings.add("Origin 선택 가능 Geometry 부족")
        }
        if (!bboxOk && hasGeometry) {
            warnings.add("Drawing Bounding Box 계산 실패")
        }
        if (summary.dxfVersion == UNKNOWN_VERSION) {
            warnings.add("DXF Version 미확인")
            guidance.add("DXF Version을 확인하십시오.")
        }

        return DxfImportValidation(
            hasLayers = hasLayers,
            conveyorLayerExists = conveyorLayerExists,
            hasGeometry = hasGeometry,
            hasRouteCandidates = hasRouteCandidates,
            calibrationGeometryOk = calibrationGeometryOk,
            originSelectable = originSelectable,
            boundingBoxOk = bboxOk,
            emptyDrawing = emptyDrawing,
            warnings = warnings.distinct(),
            errors = errors.distinct(),
            guidance = guidance.distinct(),
        )
    }

    private fun peekDxfVersion(sourcePath: String): String {
        val file = File(sourcePath)
        if (!file.isFile || !file.name.endsWith(".dxf", ignoreCase = true)) {
            return if (sourcePath.contains("stub://", ignoreCase = true)) "STUB" else UNKNOWN_VERSION
        }
        return runCatching {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { reader ->
                var line: String?
                var expectVersion = false
                while (reader.readLine().also { line = it } != null) {
                    val t = line!!.trim()
                    if (expectVersion) {
                        if (t.isNotEmpty()) return t
                        expectVersion = false
                    }
                    if (t == "\$ACADVER") expectVersion = true
                    if (t == "ENTITIES") break
                }
                UNKNOWN_VERSION
            }
        }.getOrDefault(UNKNOWN_VERSION)
    }

    private fun computeBoundingBox(geometry: GeometryModel): BBox? {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var any = false
        fun add(x: Double, y: Double) {
            any = true
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        geometry.lines.forEach {
            add(it.start.x, it.start.y)
            add(it.end.x, it.end.y)
        }
        geometry.polylines.forEach { pl ->
            pl.points.forEach { add(it.x, it.y) }
        }
        geometry.arcs.forEach {
            add(it.center.x - it.radius, it.center.y - it.radius)
            add(it.center.x + it.radius, it.center.y + it.radius)
        }
        geometry.circles.forEach {
            add(it.center.x - it.radius, it.center.y - it.radius)
            add(it.center.x + it.radius, it.center.y + it.radius)
        }
        geometry.texts.forEach { add(it.position.x, it.position.y) }
        if (!any) return null
        return BBox(minX, minY, maxX, maxY)
    }

    private data class EntityCounts(
        val polylines: Int,
        val lines: Int,
        val arcs: Int,
        val circles: Int,
        val texts: Int,
    ) {
        val total: Int get() = polylines + lines + arcs + circles + texts

        companion object {
            fun fromGeometry(g: GeometryModel) = EntityCounts(
                polylines = g.polylines.size,
                lines = g.lines.size,
                arcs = g.arcs.size,
                circles = g.circles.size,
                texts = g.texts.size,
            )
        }
    }

    private data class BBox(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double,
    ) {
        val width: Double get() = maxX - minX
        val height: Double get() = maxY - minY
    }

    private const val UNKNOWN_VERSION = "UNKNOWN"
}
