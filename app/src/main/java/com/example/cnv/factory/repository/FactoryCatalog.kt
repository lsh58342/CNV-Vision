package com.example.cnv.factory.repository

import com.example.cnv.analysis.InspectionAnalysisRepository
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Building
import com.example.cnv.factory.model.Floor
import com.example.cnv.inspection.db.InspectionDbGate

/**
 * Composition-facing catalog of context-scoped repositories.
 */
class FactoryCatalog(
    val factories: FactoryRepository = FactoryRepository(),
    val buildings: BuildingRepository = BuildingRepository(),
    val floors: FloorRepository = FloorRepository(),
    val drawings: DrawingRepository = DrawingRepository(),
    val zones: ZoneRepository = ZoneRepository(),
    val routes: ContextRouteRepository = ContextRouteRepository(),
    val inspections: ZoneInspectionRepository = ZoneInspectionRepository(),
    val heatMaps: HeatMapRepository = HeatMapRepository(),
    val calibrations: CalibrationRepository = CalibrationRepository(),
    val csvMetadata: CsvMetadataRepository = CsvMetadataRepository(),
    val replayMetadata: ReplayMetadataRepository = ReplayMetadataRepository(),
    val conveyorProfiles: ConveyorProfileRepository = ConveyorProfileRepository(),
) {
    /** Lazy to avoid catalog construction recursion. */
    val analysis: InspectionAnalysisRepository by lazy { InspectionAnalysisRepository(this) }

    /**
     * Cascade-delete Drawing and all Drawing-owned artifacts.
     */
    fun deleteDrawingCascade(drawingId: String): Boolean {
        val drawing = drawings.get(drawingId) ?: return false
        zones.removeForDrawing(drawingId)
        calibrations.removeForDrawing(drawingId)
        heatMaps.removeForDrawing(drawingId)
        csvMetadata.removeForDrawing(drawingId)
        replayMetadata.removeForDrawing(drawingId)
        conveyorProfiles.deleteAsync(drawingId)
        analysis.invalidateDrawing(drawingId)
        InspectionDbGate.execute {
            inspections.removeForDrawing(drawingId)
        }
        val ctx = CurrentContext.get()
        if (drawing.routeId != null && (ctx.routeId == drawing.routeId || routes.currentRoute()?.id == drawing.routeId)) {
            routes.underlying().clear()
        }
        if (ctx.drawingId == drawingId) {
            ctx.clearDrawing()
        }
        return drawings.delete(drawingId)
    }

    /**
     * Delete one Inspection Session and related Drawing metadata (async Room).
     */
    fun deleteInspectionSessionAsync(
        drawingId: String,
        sessionId: String,
        onDone: (() -> Unit)? = null,
    ) {
        InspectionDbGate.submit(
            block = {
                inspections.deleteSession(sessionId)
                heatMaps.removeSessionFromLayer(drawingId, sessionId)
                csvMetadata.removeForSession(drawingId, sessionId)
                replayMetadata.removeForSession(drawingId, sessionId)
                analysis.invalidate(sessionId)
            },
            onMain = { onDone?.invoke() },
        )
    }

    /** Hydrate in-memory Drawing.conveyorProfile from Room (STEP 15-4). */
    fun hydrateConveyorProfilesAsync(onDone: (() -> Unit)? = null) {
        conveyorProfiles.loadAllAsync { map ->
            map.forEach { (drawingId, profile) ->
                val drawing = drawings.get(drawingId) ?: return@forEach
                drawings.upsert(drawing.copy(conveyorProfile = profile))
            }
            onDone?.invoke()
        }
    }

    fun deleteFloorCascade(floorId: String): Boolean {
        drawings.forFloor(floorId).map { it.id }.forEach { deleteDrawingCascade(it) }
        val ctx = CurrentContext.get()
        val buildingId = ctx.buildingId
        val removed = floors.delete(floorId)
        if (removed && ctx.floorId == floorId && buildingId != null) {
            ctx.selectBuilding(buildingId)
        }
        return removed
    }

    fun deleteBuildingCascade(buildingId: String): Boolean {
        floors.forBuilding(buildingId).map { it.id }.forEach { deleteFloorCascade(it) }
        val ctx = CurrentContext.get()
        val factoryId = ctx.factoryId
        val removed = buildings.delete(buildingId)
        if (removed && ctx.buildingId == buildingId && factoryId != null) {
            ctx.selectFactory(factoryId)
        }
        return removed
    }

    fun renameBuilding(id: String, name: String): Building? {
        val building = buildings.get(id) ?: return null
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val updated = building.copy(name = trimmed)
        buildings.upsert(updated)
        return updated
    }

    fun renameFloor(id: String, name: String): Floor? {
        val floor = floors.get(id) ?: return null
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val updated = floor.copy(name = trimmed)
        floors.upsert(updated)
        return updated
    }

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
