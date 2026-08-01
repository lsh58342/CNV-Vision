package com.example.cnv.dwg

/**
 * 2D drawing coordinate (DWG units). Not mapped to route topology yet (STEP 10-2).
 */
data class Point2d(
    val x: Double,
    val y: Double,
) {
    fun distanceTo(other: Point2d): Double {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
