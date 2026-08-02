package com.example.cnv.dwg

/**
 * Developer-facing DXF import diagnostics logger (STEP 20-9).
 */
object DxfImportDiagnosticsLogger {

    private const val TAG = "CNV.DxfImport"

    fun log(report: DxfImportReport) {
        val s = report.summary
        val v = report.validation
        println("LOG[$TAG][DXF_VERSION] ${s.dxfVersion}")
        println("LOG[$TAG][LAYER_LIST] count=${s.layerCount} names=${s.layerNames}")
        println(
            "LOG[$TAG][ENTITY_SUMMARY] total=${s.entityCount} " +
                "pl=${s.polylineCount} ln=${s.lineCount} arc=${s.arcCount} " +
                "cir=${s.circleCount} txt=${s.textCount} " +
                "block=${s.blockCount} insert=${s.insertCount}",
        )
        println(
            "LOG[$TAG][GEOMETRY_SUMMARY] geometry=${s.geometryCount} " +
                "size=${s.drawingWidth}x${s.drawingHeight} " +
                "bbox=(${s.boundingBoxMinX},${s.boundingBoxMinY})-(${s.boundingBoxMaxX},${s.boundingBoxMaxY})",
        )
        println(
            "LOG[$TAG][ROUTE_SUMMARY] layer=${s.selectedConveyorLayer} " +
                "candidates=${s.routeCandidateCount}",
        )
        println(
            "LOG[$TAG][VALIDATION_SUMMARY] status=${report.status} " +
                "layers=${v.hasLayers} conveyor=${v.conveyorLayerExists} " +
                "geometry=${v.hasGeometry} route=${v.hasRouteCandidates} " +
                "calib=${v.calibrationGeometryOk} origin=${v.originSelectable} " +
                "bbox=${v.boundingBoxOk} empty=${v.emptyDrawing}",
        )
        v.warnings.forEach { println("LOG[$TAG][WARNING] $it") }
        v.errors.forEach { println("LOG[$TAG][ERROR] $it") }
        v.guidance.forEach { println("LOG[$TAG][GUIDANCE] $it") }
        report.parseError?.let { println("LOG[$TAG][PARSE_ERROR] $it") }
    }
}
