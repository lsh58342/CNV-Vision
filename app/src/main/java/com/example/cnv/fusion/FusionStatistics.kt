package com.example.cnv.fusion

/**
 * Aggregate counters for debug / future analytics.
 */
data class FusionStatistics(
    val fusedCount: Long = 0L,
    val distanceOnlyCount: Long = 0L,
    val shockOnlyCount: Long = 0L,
    val rejectedCount: Long = 0L,
    val averageConfidence: Float = 0f,
    val averageTimestampDelayNs: Long = 0L,
    val lastTimestampNs: Long = 0L,
)
