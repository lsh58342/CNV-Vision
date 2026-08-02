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
)
