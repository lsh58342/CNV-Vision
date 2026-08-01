package com.example.cnv.dwg

/**
 * Inserted block reference. Nested geometry may be empty until a real DWG reader expands it.
 */
data class BlockModel(
    val id: String,
    val layerName: String,
    val name: String,
    val insertionPoint: Point2d,
    val rotationDeg: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
)
