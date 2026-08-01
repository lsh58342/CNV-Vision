package com.example.cnv.zone.dashboard

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.CalibrationRepository
import com.example.cnv.factory.repository.FactoryCatalog

/**
 * Builds [ZoneDashboardState] from Current Context repositories (read-only).
 * Zone readiness is derived from its parent Drawing.
 */
class ZoneDashboardController(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
    private val context: CurrentContext = CurrentContext.get(),
) {

    fun load(): ZoneDashboardState {
        val zone = catalog.zones.current(context) ?: return ZoneDashboardState()
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
        val history = catalog.inspections.historyForDrawing(zone.drawingId)
        val heatMaps = catalog.heatMaps.forDrawing(zone.drawingId)
        val calReady = calibration?.ready == true ||
            drawing?.calibrationReady == true ||
            zone.calibrationVersion != null
        val dwgReady = drawing?.dwgRegistered == true
        val routeReady = drawing?.routeId != null && catalog.routes.hasRoute()
        return ZoneDashboardState(
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
}
