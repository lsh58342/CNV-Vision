package com.example.cnv.ui.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cnv.factory.context.AccessRole
import com.example.cnv.factory.context.AppMode
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.context.canAccessCommissioning
import com.example.cnv.factory.model.Building
import com.example.cnv.factory.model.Factory
import com.example.cnv.factory.model.Floor
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.seed.FactorySeedData
import com.example.cnv.zone.dashboard.ZoneDashboardController
import com.example.cnv.zone.dashboard.ZoneDashboardState

/**
 * Site hierarchy ViewModel — screens do not touch repositories directly.
 */
class SiteNavigationViewModel : ViewModel() {

    private val catalog: FactoryCatalog = FactoryCatalog.get()
    private val context: CurrentContext = CurrentContext.get()

    private val _factories = MutableLiveData<List<Factory>>(emptyList())
    val factories: LiveData<List<Factory>> = _factories

    private val _buildings = MutableLiveData<List<Building>>(emptyList())
    val buildings: LiveData<List<Building>> = _buildings

    private val _floors = MutableLiveData<List<Floor>>(emptyList())
    val floors: LiveData<List<Floor>> = _floors

    private val _zones = MutableLiveData<List<Zone>>(emptyList())
    val zones: LiveData<List<Zone>> = _zones

    private val _dashboard = MutableLiveData(ZoneDashboardState())
    val dashboard: LiveData<ZoneDashboardState> = _dashboard

    private val _contextSummary = MutableLiveData("")
    val contextSummary: LiveData<String> = _contextSummary

    private val _canOpenDeveloper = MutableLiveData(false)
    val canOpenDeveloper: LiveData<Boolean> = _canOpenDeveloper

    private val _canOpenCommissioning = MutableLiveData(false)
    val canOpenCommissioning: LiveData<Boolean> = _canOpenCommissioning

    fun bootstrap() {
        FactorySeedData.ensureSeeded(catalog)
        refreshGates()
        refreshSummary()
    }

    fun loadFactories() {
        bootstrap()
        _factories.value = catalog.factories.all()
        refreshSummary()
    }

    fun selectFactory(id: String) {
        context.selectFactory(id)
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

    fun loadFloors() {
        bootstrap()
        _floors.value = catalog.floors.listForCurrentBuilding(context)
        refreshSummary()
    }

    fun selectFloor(id: String) {
        context.selectFloor(id)
        if (context.routeId == null) {
            context.selectRoute(FactorySeedData.ROUTE_DEMO)
        }
        refreshSummary()
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

    fun loadDashboard() {
        bootstrap()
        _dashboard.value = ZoneDashboardController(catalog, context).load()
        refreshSummary()
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

    private fun refreshSummary() {
        _contextSummary.value = context.summary()
    }
}
