package com.example.cnv.zone.dashboard

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.CalibrationRepository
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.repository.HeatMapRepository
import com.example.cnv.inspection.InspectionResult

/**
 * Builds [ZoneDashboardState] from Current Context repositories (read-only).
 * Zone readiness is derived from its parent Drawing.
 * STEP 15-4: Inspection history is loaded off the main thread.
 */
class ZoneDashboardController(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
    private val context: CurrentContext = CurrentContext.get(),
) {

    fun loadAsync(onResult: (ZoneDashboardState) -> Unit) {
        val zone = catalog.zones.current(context)
        if (zone == null) {
            onResult(ZoneDashboardState())
            return
        }
        val drawing = catalog.drawings.get(zone.drawingId)
        val calibration = drawing?.let { catalog.calibrations.get(it.id) }
            ?: zone.calibrationVersion?.let { version ->
                CalibrationRepository.CalibrationRef(
                    drawingId = zone.drawingId,
                    calibrationVersion = version,
                    mmPerPixel = null,
                    ready = true,
                )
            }
        val heatMaps = catalog.heatMaps.forDrawing(zone.drawingId)
        val calReady = calibration?.ready == true ||
            drawing?.calibrationReady == true ||
            zone.calibrationVersion != null
        val dwgReady = drawing?.dwgRegistered == true
        val routeReady = drawing?.routeId != null && catalog.routes.hasRoute()
        catalog.inspections.historyForDrawingAsync(zone.drawingId) { history ->
            onResult(
                buildState(
                    zone = zone,
                    drawing = drawing,
                    calibration = calibration,
                    history = history,
                    heatMaps = heatMaps,
                    calReady = calReady,
                    dwgReady = dwgReady,
                    routeReady = routeReady,
                ),
            )
        }
    }

    private fun buildState(
        zone: Zone,
        drawing: Drawing?,
        calibration: CalibrationRepository.CalibrationRef?,
        history: List<InspectionResult>,
        heatMaps: List<HeatMapRepository.HeatMapRef>,
        calReady: Boolean,
        dwgReady: Boolean,
        routeReady: Boolean,
    ): ZoneDashboardState = ZoneDashboardState(
        zone = zone,
        dwgReady = dwgReady,
        calibrationReady = calReady,
        calibration = calibration,
        lastInspection = history.lastOrNull(),
        inspectionHistoryCount = history.size,
        heatMapCount = heatMaps.size,
        latestHeatMap = heatMaps.lastOrNull(),
        canStartInspection = dwgReady &&
            routeReady &&
            drawing?.routeLocked == true &&
            zone.start.isDefined() &&
            zone.end.isDefined(),
    )
}
