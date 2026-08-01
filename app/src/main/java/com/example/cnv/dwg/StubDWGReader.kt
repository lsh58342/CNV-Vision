package com.example.cnv.dwg

/**
 * Stand-in reader that fabricates conveyor geometry for debug / CI.
 * Replace with ODA / LibreDWG-backed implementation later — [DWGImporter] depends only on [DWGReader].
 */
class StubDWGReader : DWGReader {

    override fun open(sourcePath: String): DWGDocument {
        val name = sourcePath.substringAfterLast('/').substringAfterLast('\\').ifBlank { "stub.dwg" }
        return DWGDocument(sourcePath = sourcePath, fileName = name, nativeHandle = STUB_HANDLE)
    }

    override fun readLayers(document: DWGDocument): List<DWGLayer> {
        return listOf(
            DWGLayer("0"),
            DWGLayer("CONVEYOR"),
            DWGLayer("STRUCTURE"),
            DWGLayer("TEXT"),
        )
    }

    override fun readEntities(document: DWGDocument): DWGEntityBag {
        // Two parallel polylines on CONVEYOR + one short structure polyline on STRUCTURE.
        val left = PolylineModel(
            id = "pl-left",
            layerName = "CONVEYOR",
            points = listOf(
                Point2d(0.0, 0.0),
                Point2d(2_500.0, 0.0),
                Point2d(5_000.0, 0.0),
            ),
        )
        val right = PolylineModel(
            id = "pl-right",
            layerName = "CONVEYOR",
            points = listOf(
                Point2d(0.0, 40.0),
                Point2d(2_500.0, 40.0),
                Point2d(5_000.0, 40.0),
            ),
        )
        val structure = PolylineModel(
            id = "pl-structure",
            layerName = "STRUCTURE",
            points = listOf(Point2d(-100.0, -100.0), Point2d(-50.0, -100.0)),
        )
        val label = TextModel(
            id = "txt-1",
            layerName = "TEXT",
            position = Point2d(100.0, 80.0),
            content = "CNV MAIN",
        )
        val guideLine = LineModel(
            id = "ln-1",
            layerName = "CONVEYOR",
            start = Point2d(5_000.0, 0.0),
            end = Point2d(7_500.0, 0.0),
        )
        return DWGEntityBag(
            polylines = listOf(left, right, structure),
            lines = listOf(guideLine),
            texts = listOf(label),
        )
    }

    companion object {
        private const val STUB_HANDLE = "stub-dwg"
    }
}
