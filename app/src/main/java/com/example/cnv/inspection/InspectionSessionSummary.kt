package com.example.cnv.inspection

import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.speed.SpeedValidationSummary

/**
 * Session summary for History (Drawing-scoped). Built at finish time (STEP 13).
 * Includes Conveyor Profile snapshot from session start (STEP 15-1).
 * Includes Speed Validation aggregates (STEP 15-2).
 */
data class InspectionSessionSummary(
    val sessionId: String,
    val drawingId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val totalDistanceMm: Float,
    val shockCount: Int,
    val averageSpeedMmPerSec: Float,
    val maximumShock: Float,
    val coverage: Float,
    val inspectionVersion: String,
    val appVersion: String,
    val conveyorProfile: ConveyorProfileSnapshot = ConveyorProfileSnapshot.empty(),
    val speedValidation: SpeedValidationSummary = SpeedValidationSummary.EMPTY,
    /** Rule catalog version frozen at Inspection start (STEP 18). */
    val ruleCatalogVersion: Int = 0,
    /** Full Inspection Profile snapshot at start (STEP 19-2). */
    val inspectionProfileJson: String = "",
    /** Route geometry frozen at Inspection start (STEP 20-3). */
    val routeSnapshotJson: String = "",
    /** Analysis Result JSON at finish (STEP 20-3). */
    val analysisResultJson: String = "",
    /** Rule Result JSON at finish (STEP 20-3). */
    val ruleResultJson: String = "",
    /** HeatMap points JSON for History (STEP 20-3). */
    val heatPointsJson: String = "",
    /** Excel archive path (STEP 20-3). */
    val excelFileUri: String = "",
    val excelFileName: String = "",
)

/**
 * Loaded session with optional events (for future HeatMap / Replay).
 */
data class PersistedInspectionSession(
    val summary: InspectionSessionSummary,
    val events: List<PersistedInspectionEvent> = emptyList(),
)

data class PersistedInspectionEvent(
    val id: Long,
    val sessionId: String,
    val drawingId: String,
    val timestampNs: Long,
    val distanceMm: Float,
    val routePosition: String,
    val hasShock: Boolean,
    val shockStrength: Float,
    val trackingConfidence: Float,
    val eventType: String,
    val routePositionMm: Float = 0f,
    val worldX: Float = 0f,
    val worldY: Float = 0f,
    val speedMmPerSec: Float = 0f,
    val peakG: Float = 0f,
    val movingAverageG: Float = 0f,
    val zoneName: String = "",
    val segmentId: String = "",
    val headingDeg: Float = 0f,
    val distanceToRouteMm: Float = 0f,
    val trackingState: String = "",
    val clipPath: String = "",
)
