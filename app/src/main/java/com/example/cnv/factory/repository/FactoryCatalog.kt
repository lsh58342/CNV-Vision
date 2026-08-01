package com.example.cnv.factory.repository

/**
 * Composition-facing catalog of context-scoped repositories.
 */
class FactoryCatalog(
    val factories: FactoryRepository = FactoryRepository(),
    val buildings: BuildingRepository = BuildingRepository(),
    val floors: FloorRepository = FloorRepository(),
    val zones: ZoneRepository = ZoneRepository(),
    val routes: ContextRouteRepository = ContextRouteRepository(),
    val inspections: ZoneInspectionRepository = ZoneInspectionRepository(),
    val heatMaps: HeatMapRepository = HeatMapRepository(),
    val calibrations: CalibrationRepository = CalibrationRepository(),
    val floorSetups: FloorSetupRepository = FloorSetupRepository(),
) {
    companion object {
        @Volatile
        private var instance: FactoryCatalog? = null

        fun get(): FactoryCatalog =
            instance ?: synchronized(this) {
                instance ?: FactoryCatalog().also { instance = it }
            }

        /** Test / reset hook. */
        fun replace(catalog: FactoryCatalog) {
            synchronized(this) { instance = catalog }
        }
    }
}
