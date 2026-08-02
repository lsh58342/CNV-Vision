package com.example.cnv.replay

/**
 * Current Replay cursor position exposed via Engine API.
 */
data class ReplayPosition(
    val frameIndex: Int = 0,
    val frameCount: Int = 0,
    val timestampNs: Long = 0L,
    val elapsedMs: Long = 0L,
    val routePositionMm: Float = 0f,
    val progress01: Float = 0f,
    val drawingX: Double? = null,
    val drawingY: Double? = null,
) {
    companion object {
        val EMPTY = ReplayPosition()
    }
}
