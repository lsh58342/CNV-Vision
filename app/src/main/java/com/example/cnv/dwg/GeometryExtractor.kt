package com.example.cnv.dwg

/**
 * Converts reader entity bags + layers into a normalized [GeometryModel].
 * Applies [DWGConfig.importTolerance] when collapsing near-duplicate vertices.
 */
class GeometryExtractor(
    private val config: DWGConfig = DWGConfig.DEFAULT,
) {

    fun extract(
        fileName: String,
        layers: List<DWGLayer>,
        entities: DWGEntityBag,
    ): GeometryModel {
        return GeometryModel(
            fileName = fileName,
            layers = layers,
            polylines = entities.polylines.map { sanitizePolyline(it) },
            lines = entities.lines,
            arcs = entities.arcs,
            circles = entities.circles,
            texts = entities.texts,
            blocks = entities.blocks,
        )
    }

    private fun sanitizePolyline(polyline: PolylineModel): PolylineModel {
        if (polyline.points.size < 2) return polyline
        val cleaned = mutableListOf<Point2d>()
        for (point in polyline.points) {
            val last = cleaned.lastOrNull()
            if (last == null || last.distanceTo(point) > config.importTolerance) {
                cleaned.add(point)
            }
        }
        return polyline.copy(points = cleaned)
    }
}
