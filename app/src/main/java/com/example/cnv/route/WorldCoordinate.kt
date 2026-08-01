package com.example.cnv.route

/**
 * World-space point used by future CAD Viewer (not drawn in this STEP).
 */
data class WorldCoordinate(
    val x: Double,
    val y: Double,
) {
    fun distanceTo(other: WorldCoordinate): Double {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
