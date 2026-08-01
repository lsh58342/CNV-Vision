package com.example.cnv.zone.dashboard

import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.CalibrationRepository
import com.example.cnv.factory.repository.HeatMapRepository
import com.example.cnv.inspection.InspectionResult

/**
 * Read-only Zone Dashboard view-state for Operation Mode.
 */
data class ZoneDashboardState(
    val zone: Zone? = null,
    val dwgReady: Boolean = false,
    val calibrationReady: Boolean = false,
    val calibration: CalibrationRepository.CalibrationRef? = null,
    val lastInspection: InspectionResult? = null,
    val inspectionHistoryCount: Int = 0,
    val heatMapCount: Int = 0,
    val latestHeatMap: HeatMapRepository.HeatMapRef? = null,
    val canStartInspection: Boolean = false,
)
