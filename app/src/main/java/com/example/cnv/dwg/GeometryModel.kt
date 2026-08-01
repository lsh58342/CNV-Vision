package com.example.cnv.dwg

/**
 * Internal geometry snapshot independent of vendor DWG SDK.
 */
data class GeometryModel(
    val fileName: String,
    val layers: List<DWGLayer>,
    val polylines: List<PolylineModel> = emptyList(),
    val lines: List<LineModel> = emptyList(),
    val arcs: List<ArcModel> = emptyList(),
    val circles: List<CircleModel> = emptyList(),
    val texts: List<TextModel> = emptyList(),
    val blocks: List<BlockModel> = emptyList(),
) {
    fun layerNames(): List<String> = layers.map { it.name }

    fun polylinesOnLayer(layerName: String): List<PolylineModel> =
        polylines.filter { it.layerName.equals(layerName, ignoreCase = true) }

    fun linesOnLayer(layerName: String): List<LineModel> =
        lines.filter { it.layerName.equals(layerName, ignoreCase = true) }
}
