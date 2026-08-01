package com.example.cnv.zone.dashboard

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.CalibrationRepository
import com.example.cnv.factory.repository.FactoryCatalog

/**
 * Builds [ZoneDashboardState] from Current Context repositories (read-only).
 */
class ZoneDashboardController(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
    private val context: CurrentContext = CurrentContext.get(),
) {

    fun load(): ZoneDashboardState {
        val zone = catalog.zones.current(context) ?: return ZoneDashboardState()
        val calibration = catalog.calibrations.get(zone.id)
            ?: zone.calibrationVersion?.let { version ->
                CalibrationRepository.CalibrationRef(
                    zoneId = zone.id,
                    calibrationVersion = version,
                    mmPerPixel = null,
                    ready = true,
                )
            }
        val history = catalog.inspections.historyForZone(zone.id)
        val heatMaps = catalog.heatMaps.forZone(zone.id)
        val calReady = calibration?.ready == true || zone.calibrationVersion != null
        return ZoneDashboardState(
            zone = zone,
            dwgReady = zone.dwgRegistered,
            calibrationReady = calReady,
            calibration = calibration,
            lastInspection = history.lastOrNull(),
            inspectionHistoryCount = history.size,
            heatMapCount = heatMaps.size,
            latestHeatMap = heatMaps.lastOrNull(),
            canStartInspection = zone.dwgRegistered && zone.start.isDefined() && zone.end.isDefined(),
        )
    }
}
