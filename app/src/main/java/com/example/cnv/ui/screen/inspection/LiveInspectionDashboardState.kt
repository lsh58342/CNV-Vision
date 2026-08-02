package com.example.cnv.ui.screen.inspection

/**
 * Live Inspection Dashboard state (STEP 20-1).
 * Display-only snapshot — no Analysis / Rule / Distance recalculation.
 */
enum class SystemModuleState {
    READY,
    RUNNING,
    WARNING,
    ERROR,
}

data class SystemStatusSnapshot(
    val camera: SystemModuleState = SystemModuleState.READY,
    val openCv: SystemModuleState = SystemModuleState.READY,
    val tracking: SystemModuleState = SystemModuleState.READY,
    val fusion: SystemModuleState = SystemModuleState.READY,
    val replay: SystemModuleState = SystemModuleState.READY,
    val room: SystemModuleState = SystemModuleState.READY,
)

data class LiveDashboardWarning(
    val title: String,
    val detail: String,
)

data class LiveInspectionDashboardState(
    val buildingName: String = "—",
    val floorName: String = "—",
    val drawingName: String = "—",
    val inspectionTimeLabel: String = "—",
    val elapsedSec: Double = 0.0,
    val currentZoneName: String = "—",
    val routePositionMm: Float = 0f,
    val coordinateX: Float? = null,
    val coordinateY: Float? = null,
    val currentSpeedMPerMin: Float? = null,
    val nominalSpeedMPerMin: Float? = null,
    val speedDifferenceMPerMin: Float? = null,
    val currentShock: Float = 0f,
    val maximumShock: Float = 0f,
    val averageShock: Float = 0f,
    val trackingConfidence: Float = 0f,
    val coverage: Float = 0f,
    val validationScore: Float = 0f,
    val trackingLabel: String = "IDLE",
    val sessionState: String = "IDLE",
    val running: Boolean = false,
    val system: SystemStatusSnapshot = SystemStatusSnapshot(),
    val warnings: List<LiveDashboardWarning> = emptyList(),
    /** Kept for existing 4 status cards. */
    val distanceMm: Float = 0f,
    val shockCount: Int = 0,
    val speedMismatchWarning: Boolean = false,
)

fun LiveInspectionDashboardState.toUiStatus() = InspectionUiStatus(
    trackingLabel = trackingLabel,
    distanceMm = distanceMm,
    shockCount = shockCount,
    elapsedSec = elapsedSec,
    sessionState = sessionState,
    running = running,
    speedMismatchWarning = speedMismatchWarning,
    speedValidationConfidence = validationScore.takeIf { it > 0f },
    validatedFusionConfidence = trackingConfidence.takeIf { running },
)
