package com.example.cnv.inspection

/**
 * Aggregates computed from recorded events only (no re-fusion / re-validation).
 */
data class InspectionStatistics(
    val totalDistanceMm: Float = 0f,
    val inspectionTimeMs: Long = 0L,
    val shockCount: Int = 0,
    val averageConfidence: Float = 0f,
    val maximumShockLevel: Float = 0f,
    val minimumConfidence: Float = 0f,
    val totalEvents: Int = 0,
    val routeVersion: String = "",
    val calibrationVersion: Int = 0,
) {
    companion object {
        val EMPTY = InspectionStatistics()
    }
}
