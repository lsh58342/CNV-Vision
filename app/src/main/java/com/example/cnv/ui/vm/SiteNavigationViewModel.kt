package com.example.cnv.ui.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cnv.factory.context.AccessRole
import com.example.cnv.factory.context.AppMode
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.context.canAccessCommissioning
import com.example.cnv.factory.model.Building
import com.example.cnv.factory.model.Floor
import com.example.cnv.factory.model.Zone
import com.example.cnv.dwg.DWGImporter
import com.example.cnv.dwg.StubDWGReader
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.repository.FloorSetupRepository
import com.example.cnv.factory.seed.LgesPolandSite
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.route.RouteGenerator
import com.example.cnv.zone.dashboard.ZoneDashboardController
import com.example.cnv.zone.dashboard.ZoneDashboardState
import java.util.UUID

/**
 * Site hierarchy ViewModel — LGES Poland only; no sample Building/Floor/Zone.
 */
class SiteNavigationViewModel : ViewModel() {

    data class FloorDashboardUi(
        val buildingName: String,
        val floorName: String,
        val dwgReady: Boolean,
        val routeReady: Boolean,
        val routeLocked: Boolean,
        val calibrationReady: Boolean,
        val zones: List<Zone>,
        val historyCount: Int,
    )

    private val catalog: FactoryCatalog = FactoryCatalog.get()
    private val context: CurrentContext = CurrentContext.get()

    private val _buildings = MutableLiveData<List<Building>>(emptyList())
    val buildings: LiveData<List<Building>> = _buildings

    private val _floors = MutableLiveData<List<Floor>>(emptyList())
    val floors: LiveData<List<Floor>> = _floors

    private val _zones = MutableLiveData<List<Zone>>(emptyList())
    val zones: LiveData<List<Zone>> = _zones

    private val _dashboard = MutableLiveData(ZoneDashboardState())
    val dashboard: LiveData<ZoneDashboardState> = _dashboard

    private val _floorDashboard = MutableLiveData<FloorDashboardUi?>(null)
    val floorDashboard: LiveData<FloorDashboardUi?> = _floorDashboard

    private val _contextSummary = MutableLiveData("")
    val contextSummary: LiveData<String> = _contextSummary

    private val _canOpenDeveloper = MutableLiveData(false)
    val canOpenDeveloper: LiveData<Boolean> = _canOpenDeveloper

    private val _canOpenCommissioning = MutableLiveData(false)
    val canOpenCommissioning: LiveData<Boolean> = _canOpenCommissioning

    private val _historyLines = MutableLiveData<List<InspectionResult>>(emptyList())
    val historyLines: LiveData<List<InspectionResult>> = _historyLines

    private val _latestResult = MutableLiveData<InspectionResult?>(null)
    val latestResult: LiveData<InspectionResult?> = _latestResult

    private val _factoryName = MutableLiveData(LgesPolandSite.FACTORY_NAME)
    val factoryName: LiveData<String> = _factoryName

    fun bootstrap() {
        LgesPolandSite.ensure(catalog)
        _factoryName.value = LgesPolandSite.FACTORY_NAME
        refreshGates()
        refreshSummary()
    }

    fun loadBuildings() {
        bootstrap()
        _buildings.value = catalog.buildings.listForCurrentFactory(context)
        refreshSummary()
    }

    fun selectBuilding(id: String) {
        context.selectBuilding(id)
        refreshSummary()
    }

    /** User-created Building under LGES Poland. Returns null if name blank or locked. */
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

    fun loadFloors() {
        bootstrap()
        _floors.value = catalog.floors.listForCurrentBuilding(context)
        refreshSummary()
    }

    fun selectFloor(id: String) {
        context.selectFloor(id)
        // Do not auto-create or auto-select Route.
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

    fun loadFloorDashboard() {
        bootstrap()
        val building = catalog.buildings.current(context)
        val floor = catalog.floors.current(context)
        if (building == null || floor == null) {
            _floorDashboard.value = null
            return
        }
        val setup = catalog.floorSetups.get(floor.id)
        val zones = catalog.zones.forFloor(floor.id)
        val calReady = zones.any { z ->
            catalog.calibrations.get(z.id)?.ready == true || z.calibrationVersion != null
        }
        val historyCount = zones.sumOf { catalog.inspections.historyForZone(it.id).size }
        _floorDashboard.value = FloorDashboardUi(
            buildingName = building.name,
            floorName = floor.name,
            dwgReady = setup.dwgRegistered || zones.any { it.dwgRegistered },
            routeReady = catalog.routes.hasRoute() && context.routeId != null,
            routeLocked = setup.routeLocked,
            calibrationReady = calReady,
            zones = zones,
            historyCount = historyCount,
        )
        _zones.value = zones
        refreshSummary()
    }

    fun registerDwgForCurrentFloor(): Boolean {
        val floorId = context.floorId ?: return false
        if (isFloorLocked(floorId)) return false
        catalog.floorSetups.setDwgRegistered(floorId, true)
        loadFloorDashboard()
        return true
    }

    /**
     * User-triggered Route generation after DWG registration.
     * Uses existing DWGImporter / RouteGenerator — never auto-runs at app start.
     */
    fun generateRouteForCurrentFloor(): Boolean {
        val floorId = context.floorId ?: return false
        if (isFloorLocked(floorId)) return false
        val setup = catalog.floorSetups.get(floorId)
        if (!setup.dwgRegistered) return false
        val routeRepo = catalog.routes.underlying()
        val importer = DWGImporter(reader = StubDWGReader())
        val generator = RouteGenerator(routeRepository = routeRepo)
        val dwgResult = importer.importFrom("stub://floor-$floorId.dwg")
        generator.generate(candidates = dwgResult.candidates)
        val route = routeRepo.current() ?: return false
        catalog.routes.setRoute(route)
        loadFloorDashboard()
        return true
    }

    /** Marks route id in context after user-triggered generation elsewhere. */
    fun markRouteReady(routeId: String): Boolean {
        val floorId = context.floorId ?: return false
        if (isFloorLocked(floorId)) return false
        val setup = catalog.floorSetups.get(floorId)
        if (!setup.dwgRegistered) return false
        context.selectRoute(routeId)
        loadFloorDashboard()
        return true
    }

    fun lockRouteForCurrentFloor(): Boolean {
        val floorId = context.floorId ?: return false
        if (!catalog.routes.hasRoute()) return false
        catalog.floorSetups.setRouteLocked(floorId, true)
        loadFloorDashboard()
        return true
    }

    fun unlockRouteForCurrentFloor(): Boolean {
        if (context.accessRole != AccessRole.DEVELOPER) return false
        val floorId = context.floorId ?: return false
        catalog.floorSetups.setRouteLocked(floorId, false)
        loadFloorDashboard()
        return true
    }

    fun canCreateZone(): Boolean {
        val floorId = context.floorId ?: return false
        if (isFloorLocked(floorId)) return false
        return catalog.routes.hasRoute() && context.routeId != null
    }

    fun loadZones() {
        bootstrap()
        _zones.value = catalog.zones.listForCurrentFloor(context)
        refreshSummary()
    }

    fun selectZone(id: String) {
        context.selectZone(id)
        refreshSummary()
    }

    fun renameZone(id: String, name: String): Boolean {
        if (isCurrentFloorLocked()) return false
        val zone = catalog.zones.get(id) ?: return false
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        catalog.zones.upsert(zone.copy(name = trimmed, updatedAtMs = System.currentTimeMillis()))
        loadZones()
        loadFloorDashboard()
        return true
    }

    fun deleteZone(id: String): Boolean {
        if (isCurrentFloorLocked()) return false
        val ok = catalog.zones.delete(id)
        if (context.zoneId == id) {
            context.clearZone()
        }
        loadZones()
        loadFloorDashboard()
        return ok
    }

    fun setZoneColor(id: String, colorLabel: String, colorArgb: Int): Boolean {
        if (isCurrentFloorLocked()) return false
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
        _dashboard.value = ZoneDashboardController(catalog, context).load()
        refreshSummary()
    }

    fun loadHistory() {
        bootstrap()
        _historyLines.value = catalog.inspections.historyForCurrentZone(context)
        refreshSummary()
    }

    fun loadLatestResult() {
        bootstrap()
        val zoneLatest = catalog.inspections.historyForCurrentZone(context).lastOrNull()
        _latestResult.value = zoneLatest ?: catalog.inspections.underlying().latest()
        refreshSummary()
    }

    fun currentBuildingName(): String =
        catalog.buildings.current(context)?.name ?: "—"

    fun currentFloorName(): String =
        catalog.floors.current(context)?.name ?: "—"

    fun currentZoneName(): String =
        catalog.zones.current(context)?.name ?: "—"

    fun floorSetup(): FloorSetupRepository.FloorSetup? {
        val floorId = context.floorId ?: return null
        return catalog.floorSetups.get(floorId)
    }

    fun setRole(role: AccessRole) {
        context.setAccessRole(role)
        refreshGates()
        refreshSummary()
    }

    fun enterCommissioningMode(): Boolean {
        refreshGates()
        if (_canOpenCommissioning.value != true) return false
        return context.setAppMode(AppMode.COMMISSIONING)
    }

    fun leaveCommissioningMode() {
        context.setAppMode(AppMode.OPERATION)
        refreshGates()
        refreshSummary()
    }

    fun refreshGates() {
        val role = context.accessRole
        _canOpenDeveloper.value = role.canAccessCommissioning()
        _canOpenCommissioning.value = role.canAccessCommissioning()
    }

    private fun isCurrentFloorLocked(): Boolean {
        val floorId = context.floorId ?: return false
        return isFloorLocked(floorId)
    }

    private fun isFloorLocked(floorId: String): Boolean =
        catalog.floorSetups.get(floorId).routeLocked

    private fun refreshSummary() {
        _contextSummary.value = context.summary()
    }
}
