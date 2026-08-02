package com.example.cnv.replay.analysis

/**
 * Statistics for the current Replay frame / session snapshot (STEP 16-1).
 */
data class ReplayStatistics(
    val currentTimeMs: Long = 0L,
    val elapsedMs: Long = 0L,
    val distanceMm: Float = 0f,
    val currentZoneName: String = "—",
    val hasShock: Boolean = false,
    val shockStrength: Float = 0f,
    val trackingConfidence: Float = 0f,
    val validationScore: Float = 0f,
    val routePositionMm: Float = 0f,
    val drawingX: Double? = null,
    val drawingY: Double? = null,
    val timestampNs: Long = 0L,
) {
    companion object {
        val EMPTY = ReplayStatistics()
    }
}
