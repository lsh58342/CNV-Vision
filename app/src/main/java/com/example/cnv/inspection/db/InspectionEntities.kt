package com.example.cnv.inspection.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted Inspection Session — Drawing-scoped (STEP 13).
 */
@Entity(
    tableName = "inspection_sessions",
    indices = [Index(value = ["drawingId"]), Index(value = ["sessionId"], unique = true)],
)
data class InspectionSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val drawingId: String,
    val startTimeMs: Long,
    val endTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val totalDistanceMm: Float = 0f,
    val shockCount: Int = 0,
    val averageSpeedMmPerSec: Float = 0f,
    val maximumShock: Float = 0f,
    val coverage: Float = 0f,
    val inspectionVersion: String = "1",
    val appVersion: String = "",
    val routeVersion: String = "",
    val calibrationVersion: Int = 0,
    val finished: Boolean = false,
    /** Conveyor Profile snapshot at Inspection start (STEP 15-1 / 15-4). */
    val profileNominalSpeedMPerMin: Float? = null,
    val profileSpeedTolerancePercent: Float = 5f,
    val profileDirection: String = "",
    val profileExpectedFps: Float = 0f,
    val profileMotionProfile: String = "",
    /** Speed Validation session aggregates (STEP 15-2) — for AI / History. */
    val avgExpectedSpeedMPerMin: Float = 0f,
    val avgMeasuredSpeedMPerMin: Float = 0f,
    val maxSpeedDifferenceMm: Float = 0f,
    val avgSpeedDifferenceMm: Float = 0f,
    val speedValidationScore: Float = 0f,
    /** Rule catalog version frozen at Inspection start (STEP 18). */
    val ruleCatalogVersion: Int = 0,
)

/**
 * Persisted Inspection Event — for future HeatMap / Replay (STEP 13).
 */
@Entity(
    tableName = "inspection_events",
    indices = [Index(value = ["sessionId"]), Index(value = ["drawingId"])],
)
data class InspectionEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: String,
    val drawingId: String,
    val timestampNs: Long,
    val distanceMm: Float = 0f,
    val routePosition: String = "",
    val hasShock: Boolean = false,
    val shockStrength: Float = 0f,
    val trackingConfidence: Float = 0f,
    val eventType: String = "",
)
