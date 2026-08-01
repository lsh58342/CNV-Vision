package com.example.cnv.dwg

data class PolylineModel(
    val id: String,
    val layerName: String,
    val points: List<Point2d>,
    val closed: Boolean = false,
) {
    fun length(): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until points.lastIndex) {
            sum += points[i].distanceTo(points[i + 1])
        }
        if (closed && points.size >= 2) {
            sum += points.last().distanceTo(points.first())
        }
        return sum
    }

    fun start(): Point2d? = points.firstOrNull()

    fun end(): Point2d? = points.lastOrNull()
}
