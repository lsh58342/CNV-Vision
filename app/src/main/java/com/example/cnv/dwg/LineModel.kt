package com.example.cnv.dwg

data class LineModel(
    val id: String,
    val layerName: String,
    val start: Point2d,
    val end: Point2d,
) {
    fun length(): Double = start.distanceTo(end)

    fun toPolyline(): PolylineModel =
        PolylineModel(id = id, layerName = layerName, points = listOf(start, end))
}
