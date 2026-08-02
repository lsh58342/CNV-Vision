package com.example.cnv.replay

/**
 * Engine-owned Replay statistics (STEP 16-3).
 * Produced by internal [com.example.cnv.replay.internal.ReplayStatisticsProvider].
 */
data class ReplayEngineStatistics(
    val currentDistanceMm: Float = 0f,
    val currentSpeedMmPerSec: Float = 0f,
    val currentConfidence: Float = 0f,
    val shockCount: Int = 0,
    val coverage: Float = 0f,
    val elapsedMs: Long = 0L,
    val currentTimeMs: Long = 0L,
    val routePositionMm: Float = 0f,
    val hasShock: Boolean = false,
    val shockStrength: Float = 0f,
    val currentZoneName: String = "—",
    val validationScore: Float = 0f,
    val drawingX: Double? = null,
    val drawingY: Double? = null,
    val timestampNs: Long = 0L,
) {
    companion object {
        val EMPTY = ReplayEngineStatistics()
    }
}
