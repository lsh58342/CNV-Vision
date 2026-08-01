package com.example.cnv.inspection

/**
 * Final session outcome stored by [InspectionRepository].
 */
data class InspectionResult(
    val sessionId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val routeVersion: String,
    val calibrationVersion: Int,
    val statistics: InspectionStatistics,
    val routeQualityScore: Float,
)
