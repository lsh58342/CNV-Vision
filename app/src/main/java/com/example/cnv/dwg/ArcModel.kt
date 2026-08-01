package com.example.cnv.dwg

data class ArcModel(
    val id: String,
    val layerName: String,
    val center: Point2d,
    val radius: Double,
    val startAngleDeg: Double,
    val endAngleDeg: Double,
)
