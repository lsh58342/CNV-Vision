package com.example.cnv.dwg

data class TextModel(
    val id: String,
    val layerName: String,
    val position: Point2d,
    val content: String,
    val height: Double = 1.0,
)
