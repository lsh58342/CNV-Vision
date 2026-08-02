package com.example.cnv.analysis

import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.inspection.InspectionSessionSummary

/**
 * Full Inspection Analysis output (STEP 17).
 * Replay / History / HeatMap / AI / Report consume this — they do not re-analyze events.
 */
data class InspectionAnalysisResult(
    val sessionId: String,
    val drawingId: String,
    val analyzedAtMs: Long = System.currentTimeMillis(),
    val summary: InspectionAnalysisSummary = InspectionAnalysisSummary(),
    val distance: DistanceStatistics = DistanceStatistics.EMPTY,
    val speed: SpeedStatistics = SpeedStatistics.EMPTY,
    val tracking: TrackingStatistics = TrackingStatistics.EMPTY,
    val shock: ShockStatistics = ShockStatistics.EMPTY,
    val zones: List<ZoneStatistics> = emptyList(),
    val coverage: CoverageStatistics = CoverageStatistics.EMPTY,
    val validationScore: Float = 0f,
    val conveyorProfile: ConveyorProfileSnapshot = ConveyorProfileSnapshot.empty(),
) {
    companion object {
        fun empty(sessionId: String = "", drawingId: String = "") = InspectionAnalysisResult(
            sessionId = sessionId,
            drawingId = drawingId,
        )
    }
}

data class InspectionAnalysisSummary(
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val inspectionVersion: String = "",
    val appVersion: String = "",
    val eventCount: Int = 0,
) {
    companion object {
        fun from(summary: InspectionSessionSummary, eventCount: Int) = InspectionAnalysisSummary(
            startTimeMs = summary.startTimeMs,
            endTimeMs = summary.endTimeMs,
            durationMs = summary.durationMs,
            inspectionVersion = summary.inspectionVersion,
            appVersion = summary.appVersion,
            eventCount = eventCount,
        )
    }
}

data class DistanceStatistics(
    val totalDistanceMm: Float = 0f,
    val averageDistanceMm: Float = 0f,
    val maximumDeltaMm: Float = 0f,
    val minimumDeltaMm: Float = 0f,
) {
    companion object {
        val EMPTY = DistanceStatistics()
    }
}

data class SpeedStatistics(
    val averageSpeedMmPerSec: Float = 0f,
    val maximumSpeedMmPerSec: Float = 0f,
    val minimumSpeedMmPerSec: Float = 0f,
    val nominalSpeedMPerMin: Float? = null,
    val speedDifferenceMmPerSec: Float = 0f,
) {
    companion object {
        val EMPTY = SpeedStatistics()
    }
}

data class TrackingStatistics(
    val averageConfidence: Float = 0f,
    val minimumConfidence: Float = 0f,
    val lowConfidenceCount: Int = 0,
    val trackingLossCount: Int = 0,
) {
    companion object {
        val EMPTY = TrackingStatistics()
    }
}

data class ShockStatistics(
    val shockCount: Int = 0,
    val maximumShock: Float = 0f,
    val averageShock: Float = 0f,
    val shockDensityPerMeter: Float = 0f,
) {
    companion object {
        val EMPTY = ShockStatistics()
    }
}

data class ZoneStatistics(
    val zoneId: String,
    val zoneName: String,
    val distanceMm: Float = 0f,
    val shockCount: Int = 0,
    val coverage: Float = 0f,
    val inspectionTimeMs: Long = 0L,
)

data class CoverageStatistics(
    val drawingCoverage: Float = 0f,
    val routeCoverage: Float = 0f,
    val inspectionRatio: Float = 0f,
) {
    companion object {
        val EMPTY = CoverageStatistics()
    }
}
