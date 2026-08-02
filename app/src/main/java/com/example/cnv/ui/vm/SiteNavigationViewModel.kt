package com.example.cnv.ui.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cnv.dwg.CadReaderFactory
import com.example.cnv.dwg.DWGImporter
import com.example.cnv.factory.context.AppMode
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Building
import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.Floor
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.CalibrationRepository
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.seed.LgesPolandSite
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.route.RouteGenerator
import com.example.cnv.zone.dashboard.ZoneDashboardController
import com.example.cnv.zone.dashboard.ZoneDashboardState
import java.util.UUID

/**
 * Site hierarchy ViewModel — Drawing-centric (LGES Poland only).
 */
class SiteNavigationViewModel : ViewModel() {

    data class DrawingDashboardUi(
        val buildingName: String,
        val floorName: String,
        val drawingName: String,
        val description: String,
        val registeredAtMs: Long,
        val dwgReady: Boolean,
        val originSet: Boolean,
        val calibrationReady: Boolean,
        val routeReady: Boolean,
        val routeLocked: Boolean,
        val zoneCount: Int,
        val zones: List<Zone>,
        val lastInspectionLabel: String,
        val historyCount: Int,
        val heatMapCount: Int,
        val csvCount: Int,
        val replayCount: Int,
    )

    private val catalog: FactoryCatalog = FactoryCatalog.get()
    private val context: CurrentContext = CurrentContext.get()
    private var conveyorProfilesHydrated = false

    private val _buildings = MutableLiveData<List<Building>>(emptyList())
    val buildings: LiveData<List<Building>> = _buildings

    private val _floors = MutableLiveData<List<Floor>>(emptyList())
    val floors: LiveData<List<Floor>> = _floors

    private val _drawings = MutableLiveData<List<Drawing>>(emptyList())
    val drawings: LiveData<List<Drawing>> = _drawings

    private val _zones = MutableLiveData<List<Zone>>(emptyList())
    val zones: LiveData<List<Zone>> = _zones

    private val _dashboard = MutableLiveData(ZoneDashboardState())
    val dashboard: LiveData<ZoneDashboardState> = _dashboard

    private val _drawingDashboard = MutableLiveData<DrawingDashboardUi?>(null)
    val drawingDashboard: LiveData<DrawingDashboardUi?> = _drawingDashboard

    private val _contextSummary = MutableLiveData("")
    val contextSummary: LiveData<String> = _contextSummary

    private val _historyLines = MutableLiveData<List<InspectionResult>>(emptyList())
    val historyLines: LiveData<List<InspectionResult>> = _historyLines

    private val _latestResult = MutableLiveData<InspectionResult?>(null)
    val latestResult: LiveData<InspectionResult?> = _latestResult

    private val _factoryName = MutableLiveData(LgesPolandSite.FACTORY_NAME)
    val factoryName: LiveData<String> = _factoryName

    fun bootstrap() {
        LgesPolandSite.ensure(catalog)
        _factoryName.value = LgesPolandSite.FACTORY_NAME
        if (!conveyorProfilesHydrated) {
            conveyorProfilesHydrated = true
            catalog.hydrateConveyorProfilesAsync {
                // Refresh drawing-bound UI once Room profiles are applied.
                if (context.drawingId != null) {
                    loadDrawingDashboard()
                }
            }
        }
        refreshSummary()
    }

    // --- Building ---

    fun loadBuildings() {
        bootstrap()
        _buildings.value = catalog.buildings.listForCurrentFactory(context)
        refreshSummary()
    }

    fun selectBuilding(id: String) {
        context.selectBuilding(id)
        refreshSummary()
    }

    fun addBuilding(name: String): Building? {
        bootstrap()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val building = Building(
            id = "building-${UUID.randomUUID()}",
            factoryId = LgesPolandSite.FACTORY_ID,
            name = trimmed,
        )
        catalog.buildings.upsert(building)
        loadBuildings()
        return building
    }

    fun renameBuilding(id: String, name: String): Boolean {
        val updated = catalog.renameBuilding(id, name) ?: return false
        if (context.buildingId == id) {
            context.selectBuilding(updated.id)
        }
        loadBuildings()
        return true
    }

    fun deleteBuilding(id: String): Boolean {
        val ok = catalog.deleteBuildingCascade(id)
        loadBuildings()
        return ok
    }

    // --- Floor ---

    fun loadFloors() {
        bootstrap()
        _floors.value = catalog.floors.listForCurrentBuilding(context)
        refreshSummary()
    }

    fun selectFloor(id: String) {
        context.selectFloor(id)
        refreshSummary()
    }

    fun addFloor(name: String): Floor? {
        bootstrap()
        val buildingId = context.buildingId ?: return null
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val floor = Floor(
            id = "floor-${UUID.randomUUID()}",
            buildingId = buildingId,
            name = trimmed,
        )
        catalog.floors.upsert(floor)
        loadFloors()
        return floor
    }

    fun renameFloor(id: String, name: String): Boolean {
        val updated = catalog.renameFloor(id, name) ?: return false
        if (context.floorId == id) {
            context.selectFloor(updated.id)
        }
        loadFloors()
        return true
    }

    fun deleteFloor(id: String): Boolean {
        val ok = catalog.deleteFloorCascade(id)
        loadFloors()
        return ok
    }

    // --- Drawing ---

    fun loadDrawings() {
        bootstrap()
        _drawings.value = catalog.drawings.listForCurrentFloor(context)
        refreshSummary()
    }

    fun selectDrawing(id: String) {
        context.selectDrawing(id)
        catalog.drawings.get(id)?.routeId?.let { context.selectRoute(it) }
        refreshSummary()
    }

    fun addDrawing(name: String, description: String, dwgUri: String?): Drawing? {
        bootstrap()
        val floorId = context.floorId ?: return null
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val drawing = Drawing(
            id = "drawing-${UUID.randomUUID()}",
            floorId = floorId,
            name = trimmed,
            description = description.trim(),
            dwgUri = dwgUri,
            registeredAtMs = now,
            dwgRegistered = !dwgUri.isNullOrBlank(),
            createdAtMs = now,
            updatedAtMs = now,
        )
        catalog.drawings.upsert(drawing)
        loadDrawings()
        return drawing
    }

    fun registerDwgForCurrentDrawing(dwgUri: String): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        catalog.drawings.upsert(
            drawing.copy(
                dwgUri = dwgUri,
                dwgRegistered = true,
                registeredAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        loadDrawingDashboard()
        return true
    }

    fun setOriginForCurrentDrawing(x: Float, y: Float): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        if (!drawing.dwgRegistered) return false
        if (drawing.originSet) return false // once only
        catalog.drawings.upsert(
            drawing.copy(
                originSet = true,
                originX = x,
                originY = y,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        loadDrawingDashboard()
        return true
    }

    /**
     * Update Drawing Conveyor Profile metadata (editable after Commissioning).
     * Does not affect active Inspection Session snapshots.
     */
    fun updateConveyorProfileForCurrentDrawing(profile: ConveyorProfile): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        val now = System.currentTimeMillis()
        val updated = profile.copy(lastUpdatedMs = now)
        catalog.drawings.upsert(
            drawing.copy(
                conveyorProfile = updated,
                updatedAtMs = now,
            ),
        )
        catalog.conveyorProfiles.saveAsync(drawing.id, updated)
        loadDrawingDashboard()
        return true
    }

    fun markCalibrationReadyForCurrentDrawing(mmPerPixel: Float? = null): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        if (!drawing.originSet) return false
        catalog.calibrations.put(
            CalibrationRepository.CalibrationRef(
                drawingId = drawing.id,
                calibrationVersion = 1,
                mmPerPixel = mmPerPixel,
                ready = true,
            ),
        )
        catalog.drawings.upsert(
            drawing.copy(calibrationReady = true, updatedAtMs = System.currentTimeMillis()),
        )
        loadDrawingDashboard()
        return true
    }

    fun generateRouteForCurrentDrawing(): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        if (!drawing.dwgRegistered || !drawing.originSet) return false
        val routeRepo = catalog.routes.underlying()
        val importer = DWGImporter(reader = CadReaderFactory.create(drawing.dwgUri.orEmpty()))
        val generator = RouteGenerator(routeRepository = routeRepo)
        val source = drawing.dwgUri ?: "stub://drawing-${drawing.id}.dwg"
        val dwgResult = importer.importFrom(source)
        generator.generate(candidates = dwgResult.candidates)
        val route = routeRepo.current() ?: return false
        catalog.routes.setRoute(route)
        catalog.drawings.upsert(
            drawing.copy(
                routeId = route.id,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        loadDrawingDashboard()
        return true
    }

    fun lockRouteForCurrentDrawing(): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeId == null || !catalog.routes.hasRoute()) return false
        if (catalog.zones.forDrawing(drawing.id).isEmpty()) return false
        catalog.drawings.upsert(
            drawing.copy(routeLocked = true, updatedAtMs = System.currentTimeMillis()),
        )
        loadDrawingDashboard()
        return true
    }

    fun unlockRouteForCurrentDrawing(): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        catalog.drawings.upsert(
            drawing.copy(routeLocked = false, updatedAtMs = System.currentTimeMillis()),
        )
        loadDrawingDashboard()
        return true
    }

    fun deleteDrawing(id: String): Boolean {
        val ok = catalog.deleteDrawingCascade(id)
        loadDrawings()
        _drawingDashboard.value = null
        return ok
    }

    fun canCreateZone(): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        return drawing.routeId != null && catalog.routes.hasRoute()
    }

    fun loadDrawingDashboard() {
        bootstrap()
        val building = catalog.buildings.current(context)
        val floor = catalog.floors.current(context)
        val drawing = catalog.drawings.current(context)
        if (building == null || floor == null || drawing == null) {
            _drawingDashboard.value = null
            return
        }
        val zones = catalog.zones.forDrawing(drawing.id)
        val heatMaps = catalog.heatMaps.forDrawing(drawing.id)
        val cal = catalog.calibrations.get(drawing.id)
        catalog.inspections.historyForDrawingAsync(drawing.id) { history ->
            _drawingDashboard.value = DrawingDashboardUi(
                buildingName = building.name,
                floorName = floor.name,
                drawingName = drawing.name,
                description = drawing.description,
                registeredAtMs = drawing.registeredAtMs,
                dwgReady = drawing.dwgRegistered,
                originSet = drawing.originSet,
                calibrationReady = drawing.calibrationReady || cal?.ready == true,
                routeReady = drawing.routeId != null && catalog.routes.hasRoute(),
                routeLocked = drawing.routeLocked,
                zoneCount = zones.size,
                zones = zones,
                lastInspectionLabel = history.lastOrNull()?.sessionId ?: "—",
                historyCount = history.size,
                heatMapCount = heatMaps.size,
                csvCount = catalog.csvMetadata.forDrawing(drawing.id).size,
                replayCount = catalog.replayMetadata.forDrawing(drawing.id).size,
            )
            _zones.value = zones
            refreshSummary()
        }
    }

    // --- Zone / Operation ---

    fun loadZones() {
        bootstrap()
        _zones.value = catalog.zones.listForCurrentDrawing(context)
        refreshSummary()
    }

    fun selectZone(id: String) {
        context.selectZone(id)
        refreshSummary()
    }

    fun renameZone(id: String, name: String): Boolean {
        if (isCurrentDrawingLocked()) return false
        val zone = catalog.zones.get(id) ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        catalog.zones.upsert(zone.copy(name = trimmed, updatedAtMs = System.currentTimeMillis()))
        loadZones()
        loadDrawingDashboard()
        return true
    }

    fun deleteZone(id: String): Boolean {
        if (isCurrentDrawingLocked()) return false
        val ok = catalog.zones.delete(id)
        if (context.zoneId == id) context.clearZone()
        loadZones()
        loadDrawingDashboard()
        return ok
    }

    fun setZoneColor(id: String, colorLabel: String, colorArgb: Int): Boolean {
        if (isCurrentDrawingLocked()) return false
        val zone = catalog.zones.get(id) ?: return false
        catalog.zones.upsert(
            zone.copy(
                colorLabel = colorLabel,
                colorArgb = colorArgb,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        loadZones()
        return true
    }

    fun loadDashboard() {
        bootstrap()
        ZoneDashboardController(catalog, context).loadAsync { state ->
            _dashboard.value = state
            refreshSummary()
        }
    }

    fun loadHistory() {
        bootstrap()
        val drawingId = context.drawingId
        if (drawingId == null) {
            _historyLines.value = emptyList()
            refreshSummary()
            return
        }
        catalog.inspections.historyForDrawingAsync(drawingId) { results ->
            _historyLines.value = results
            refreshSummary()
        }
    }

    fun loadLatestResult() {
        bootstrap()
        val drawingId = context.drawingId
        if (drawingId == null) {
            _latestResult.value = catalog.inspections.underlying().latest()
            refreshSummary()
            return
        }
        catalog.inspections.historyForDrawingAsync(drawingId) { results ->
            _latestResult.value = results.lastOrNull()
                ?: catalog.inspections.underlying().latest()
            refreshSummary()
        }
    }

    fun currentBuildingName(): String =
        catalog.buildings.current(context)?.name ?: "—"

    fun currentFloorName(): String =
        catalog.floors.current(context)?.name ?: "—"

    fun currentDrawingName(): String =
        catalog.drawings.current(context)?.name ?: "—"

    fun currentZoneName(): String =
        catalog.zones.current(context)?.name ?: "—"

    fun enterCommissioningMode(): Boolean =
        context.setAppMode(AppMode.COMMISSIONING)

    fun leaveCommissioningMode() {
        context.setAppMode(AppMode.OPERATION)
        refreshSummary()
    }

    /** Compatibility: Floor dashboard observers — maps to Drawing dashboard when a Drawing is selected. */
    @Deprecated("Use drawingDashboard")
    val floorDashboard: LiveData<DrawingDashboardUi?> = _drawingDashboard

    @Deprecated("Use loadDrawingDashboard")
    fun loadFloorDashboard() = loadDrawingDashboard()

    @Deprecated("Use unlockRouteForCurrentDrawing")
    fun unlockRouteForCurrentFloor(): Boolean = unlockRouteForCurrentDrawing()

    @Deprecated("Use generateRouteForCurrentDrawing")
    fun generateRouteForCurrentFloor(): Boolean = generateRouteForCurrentDrawing()

    @Deprecated("Use lockRouteForCurrentDrawing")
    fun lockRouteForCurrentFloor(): Boolean = lockRouteForCurrentDrawing()

    @Deprecated("Use registerDwgForCurrentDrawing")
    fun registerDwgForCurrentFloor(): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        return registerDwgForCurrentDrawing(drawing.dwgUri ?: "stub://drawing-${drawing.id}.dwg")
    }

    private fun isCurrentDrawingLocked(): Boolean =
        catalog.drawings.current(context)?.routeLocked == true

    private fun refreshSummary() {
        _contextSummary.value = context.summary()
    }
}
