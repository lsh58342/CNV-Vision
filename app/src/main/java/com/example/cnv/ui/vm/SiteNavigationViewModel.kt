package com.example.cnv.ui.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cnv.dwg.CadReaderFactory
import com.example.cnv.dwg.DWGConfig
import com.example.cnv.dwg.DWGImporter
import com.example.cnv.dwg.DxfImportAnalyzer
import com.example.cnv.dwg.DxfImportDiagnosticsStore
import com.example.cnv.dwg.DxfImportReport
import com.example.cnv.factory.context.AppMode
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.context.CurrentContextPrefs
import com.example.cnv.factory.model.Building
import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.Floor
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.CalibrationRepository
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.seed.LgesPolandSite
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.route.CoordinateMapper
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
    private var siteHierarchyHydrated = false

    /** Mapper from last RouteGenerator run (Commissioning CAD wiring). */
    @Volatile
    private var routeMapper: CoordinateMapper? = null

    fun currentRouteMapper(): CoordinateMapper? = routeMapper

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

    /** In-memory DXF import diagnostics (STEP 20-9). Not persisted. */
    private val _cadImportReport = MutableLiveData<DxfImportReport?>(null)
    val cadImportReport: LiveData<DxfImportReport?> = _cadImportReport

    private val _factoryName = MutableLiveData(LgesPolandSite.FACTORY_NAME)
    val factoryName: LiveData<String> = _factoryName

    fun bootstrap() {
        LgesPolandSite.ensure(catalog)
        _factoryName.value = LgesPolandSite.FACTORY_NAME
        if (!siteHierarchyHydrated) {
            siteHierarchyHydrated = true
            catalog.hydrateSiteHierarchyAsync {
                CurrentContextPrefs.restore(context)
                context.drawingId?.let { catalog.activateRouteForDrawing(it) }
                if (!conveyorProfilesHydrated) {
                    conveyorProfilesHydrated = true
                    catalog.hydrateConveyorProfilesAsync {
                        refreshAfterHydrate()
                    }
                } else {
                    refreshAfterHydrate()
                }
            }
        } else if (!conveyorProfilesHydrated) {
            conveyorProfilesHydrated = true
            catalog.hydrateConveyorProfilesAsync {
                refreshAfterHydrate()
            }
        }
        refreshSummary()
    }

    private fun refreshAfterHydrate() {
        _buildings.value = catalog.buildings.listForCurrentFactory(context)
        _floors.value = catalog.floors.listForCurrentBuilding(context)
        _drawings.value = catalog.drawings.listForCurrentFloor(context)
        _zones.value = catalog.zones.listForCurrentDrawing(context)
        if (context.drawingId != null) {
            loadDrawingDashboard()
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
        CurrentContextPrefs.save(context)
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
        context.selectBuilding(building.id)
        CurrentContextPrefs.save(context)
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
        CurrentContextPrefs.save(context)
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
        context.selectFloor(floor.id)
        CurrentContextPrefs.save(context)
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
        catalog.activateRouteForDrawing(id)
        catalog.drawings.get(id)?.routeId?.let { context.selectRoute(it) }
        CurrentContextPrefs.save(context)
        refreshSummary()
        loadDrawingDashboard()
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
            conveyorLayerName = if (!dwgUri.isNullOrBlank()) {
                resolveInitialConveyorLayer(peekCadLayers(dwgUri))
            } else {
                DWGConfig.DEFAULT_LAYER_FILTER
            },
            createdAtMs = now,
            updatedAtMs = now,
        )
        catalog.drawings.upsert(drawing)
        context.selectDrawing(drawing.id)
        CurrentContextPrefs.save(context)
        loadDrawings()
        if (!dwgUri.isNullOrBlank()) {
            runCadImportDiagnostics(
                sourcePath = dwgUri,
                conveyorLayer = drawing.conveyorLayerName,
            )
        }
        return drawing
    }

    fun registerDwgForCurrentDrawing(dwgUri: String): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        val layers = peekCadLayers(dwgUri)
        val layer = resolveInitialConveyorLayer(layers)
        catalog.drawings.upsert(
            drawing.copy(
                dwgUri = dwgUri,
                dwgRegistered = true,
                conveyorLayerName = layer,
                registeredAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        loadDrawingDashboard()
        runCadImportDiagnostics(sourcePath = dwgUri, conveyorLayer = layer)
        return true
    }

    /**
     * Layers available in the current Drawing CAD source (DXF/Stub).
     * Empty when no source is registered.
     */
    fun listCadLayersForCurrentDrawing(): List<String> {
        val drawing = catalog.drawings.current(context) ?: return emptyList()
        val source = drawing.dwgUri ?: return emptyList()
        return peekCadLayers(source)
    }

    fun setConveyorLayerForCurrentDrawing(layerName: String): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        val trimmed = layerName.trim()
        if (trimmed.isEmpty()) return false
        catalog.drawings.upsert(
            drawing.copy(
                conveyorLayerName = trimmed,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        loadDrawingDashboard()
        drawing.dwgUri?.let { runCadImportDiagnostics(sourcePath = it, conveyorLayer = trimmed) }
        println("LOG[CNV.GenerateRoute][LAYER_SET] drawingId=${drawing.id} layer=$trimmed")
        return true
    }

    fun currentConveyorLayerName(): String {
        val drawing = catalog.drawings.current(context) ?: return DWGConfig.DEFAULT_LAYER_FILTER
        return resolveEffectiveConveyorLayer(drawing)
    }

    /**
     * Re-run DXF import diagnostics for the current Drawing (memory-only).
     */
    fun runCadImportDiagnostics(
        sourcePath: String? = null,
        conveyorLayer: String? = null,
    ): DxfImportReport? {
        val drawing = catalog.drawings.current(context)
        val source = sourcePath ?: drawing?.dwgUri ?: return null
        val layer = conveyorLayer
            ?: drawing?.let { resolveEffectiveConveyorLayer(it) }
            ?: DWGConfig.DEFAULT_LAYER_FILTER
        val report = DxfImportAnalyzer.analyze(source, layer)
        _cadImportReport.value = report
        return report
    }

    fun latestCadImportReport(): DxfImportReport? =
        _cadImportReport.value ?: DxfImportDiagnosticsStore.latest

    /**
     * @param layerName user-selected conveyor layer; when null, uses Drawing effective layer
     * (never silently forces [DWGConfig.DEFAULT] when a real DXF layer exists).
     */
    fun generateRouteForCurrentDrawing(layerName: String? = null): Boolean {
        val drawing = catalog.drawings.current(context)
        if (drawing == null) {
            logGenerateRoute(
                selectedLayer = layerName,
                extractorLayer = null,
                candidateCount = 0,
                routeSaved = false,
                drawingId = null,
                activated = false,
                previewRouteExists = catalog.routes.hasRoute(),
                failure = "no_current_drawing",
            )
            return false
        }
        if (drawing.routeLocked) {
            logGenerateRoute(
                selectedLayer = layerName ?: drawing.conveyorLayerName,
                extractorLayer = null,
                candidateCount = 0,
                routeSaved = false,
                drawingId = drawing.id,
                activated = false,
                previewRouteExists = catalog.routes.hasRoute(),
                failure = "route_locked",
            )
            return false
        }
        if (!drawing.dwgRegistered) {
            logGenerateRoute(
                selectedLayer = layerName ?: drawing.conveyorLayerName,
                extractorLayer = null,
                candidateCount = 0,
                routeSaved = false,
                drawingId = drawing.id,
                activated = false,
                previewRouteExists = catalog.routes.hasRoute(),
                failure = "dwg_not_registered",
            )
            return false
        }
        if (!drawing.originSet) {
            logGenerateRoute(
                selectedLayer = layerName ?: drawing.conveyorLayerName,
                extractorLayer = null,
                candidateCount = 0,
                routeSaved = false,
                drawingId = drawing.id,
                activated = false,
                previewRouteExists = catalog.routes.hasRoute(),
                failure = "origin_not_set",
            )
            return false
        }

        val source = drawing.dwgUri ?: "stub://drawing-${drawing.id}.dwg"
        val selectedLayer = layerName?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolveEffectiveConveyorLayer(drawing)
        // Persist effective selection so UI / Origin preview stay aligned.
        if (!selectedLayer.equals(drawing.conveyorLayerName, ignoreCase = true)) {
            catalog.drawings.upsert(
                drawing.copy(
                    conveyorLayerName = selectedLayer,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }

        val imported = importRouteCandidates(source, selectedLayer)
        if (imported == null) {
            logGenerateRoute(
                selectedLayer = selectedLayer,
                extractorLayer = selectedLayer,
                candidateCount = 0,
                routeSaved = false,
                drawingId = drawing.id,
                activated = false,
                previewRouteExists = catalog.routes.hasRoute(),
                failure = "no_candidates",
            )
            return false
        }

        runCadImportDiagnostics(sourcePath = source, conveyorLayer = imported.layer)
        val routeRepo = catalog.routes.underlying()
        val generator = RouteGenerator(routeRepository = routeRepo)
        val generated = generator.generate(candidates = imported.candidates)
        if (generated == null) {
            logGenerateRoute(
                selectedLayer = selectedLayer,
                extractorLayer = imported.layer,
                candidateCount = imported.candidates.size,
                routeSaved = false,
                drawingId = drawing.id,
                activated = false,
                previewRouteExists = catalog.routes.hasRoute(),
                failure = "route_normalize_failed",
            )
            return false
        }
        routeMapper = generated.mapper
        // Wire through ContextRouteRepository (persist + drawingRoutes), not only underlying.
        catalog.routes.setRoute(
            generated.route,
            drawingId = drawing.id,
            mapper = generated.mapper,
        )
        val activated = catalog.activateRouteForDrawing(drawing.id)
        routeMapper = catalog.routes.underlying().currentMapper() ?: generated.mapper
        val latest = catalog.drawings.get(drawing.id) ?: drawing
        catalog.drawings.upsert(
            latest.copy(
                routeId = generated.route.id,
                conveyorLayerName = imported.layer,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        loadDrawingDashboard()
        val previewExists = catalog.routes.hasRoute()
        logGenerateRoute(
            selectedLayer = selectedLayer,
            extractorLayer = imported.layer,
            candidateCount = imported.candidates.size,
            routeSaved = previewExists,
            drawingId = drawing.id,
            activated = activated,
            previewRouteExists = previewExists,
            failure = null,
        )
        return previewExists
    }

    /**
     * Loads Route into [RouteRepository] for Origin CAD pick without marking Drawing.routeId.
     */
    fun ensureRoutePreviewForCurrentDrawing(): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (!drawing.dwgRegistered || drawing.routeLocked) return false
        if (catalog.activateRouteForDrawing(drawing.id)) {
            val existingMapper = catalog.routes.underlying().currentMapper()
            if (existingMapper != null) {
                routeMapper = existingMapper
                println(
                    "LOG[CNV.GenerateRoute][PREVIEW] drawingId=${drawing.id} " +
                        "source=restored+mapper hasRoute=${catalog.routes.hasRoute()}",
                )
                return true
            }
            println(
                "LOG[CNV.GenerateRoute][PREVIEW] drawingId=${drawing.id} " +
                    "source=restored_without_mapper — regenerating geometry mapper",
            )
            // Fall through: Route topology exists but Drawing coordinates are missing.
        }
        if (catalog.routes.hasRoute() && catalog.routes.underlying().currentMapper() != null) {
            routeMapper = catalog.routes.underlying().currentMapper()
            println(
                "LOG[CNV.GenerateRoute][PREVIEW] drawingId=${drawing.id} " +
                    "source=active_repo+mapper hasRoute=true",
            )
            return true
        }
        val source = drawing.dwgUri ?: "stub://drawing-${drawing.id}.dwg"
        val selectedLayer = resolveEffectiveConveyorLayer(drawing)
        val imported = importRouteCandidates(source, selectedLayer) ?: run {
            logGenerateRoute(
                selectedLayer = selectedLayer,
                extractorLayer = selectedLayer,
                candidateCount = 0,
                routeSaved = false,
                drawingId = drawing.id,
                activated = false,
                previewRouteExists = false,
                failure = "preview_no_candidates",
            )
            return false
        }
        if (!imported.layer.equals(drawing.conveyorLayerName, ignoreCase = true)) {
            catalog.drawings.upsert(
                drawing.copy(
                    conveyorLayerName = imported.layer,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
        val generator = RouteGenerator(routeRepository = catalog.routes.underlying())
        val generated = generator.generate(candidates = imported.candidates) ?: return false
        routeMapper = generated.mapper
        catalog.routes.setRoute(
            generated.route,
            drawingId = drawing.id,
            mapper = generated.mapper,
        )
        val activated = catalog.activateRouteForDrawing(drawing.id)
        // Keep ViewModel mapper in sync after activate (repository is source of truth).
        routeMapper = catalog.routes.underlying().currentMapper() ?: generated.mapper
        logGenerateRoute(
            selectedLayer = selectedLayer,
            extractorLayer = imported.layer,
            candidateCount = imported.candidates.size,
            routeSaved = catalog.routes.hasRoute(),
            drawingId = drawing.id,
            activated = activated,
            previewRouteExists = catalog.routes.hasRoute(),
            failure = null,
        )
        return catalog.routes.hasRoute()
    }

    /**
     * Prefer the user/Drawing layer when it exists in the DXF.
     * Never try missing [DWGConfig.DEFAULT_LAYER_FILTER] before real file layers.
     */
    private fun importRouteCandidates(
        sourcePath: String,
        preferredLayer: String,
    ): LayerImport? {
        val fileLayers = peekCadLayers(sourcePath)
        val preferred = preferredLayer.trim().ifBlank { DWGConfig.DEFAULT_LAYER_FILTER }
        val preferredInFile = fileLayers.any { it.equals(preferred, ignoreCase = true) }
        val ordered = LinkedHashSet<String>()
        if (preferredInFile) {
            ordered.add(fileLayers.first { it.equals(preferred, ignoreCase = true) })
        }
        fileLayers.forEach { ordered.add(it) }
        if (!preferredInFile) {
            // Try saved selection last (may be stale CONVEYOR after restore).
            ordered.add(preferred)
        }
        if (ordered.none { it.equals("0", ignoreCase = true) }) {
            ordered.add("0")
        }

        println(
            "LOG[CNV.GenerateRoute][LAYER_ORDER] preferred=$preferred " +
                "preferredInFile=$preferredInFile fileLayers=$fileLayers order=$ordered",
        )

        for (layer in ordered) {
            val result = runCatching {
                DWGImporter(reader = CadReaderFactory.create(sourcePath))
                    .importFrom(sourcePath, layerName = layer)
            }.onFailure { err ->
                println("LOG[CNV.GenerateRoute][IMPORT_ERROR] layer=$layer error=${err.message}")
            }.getOrNull()
            val count = result?.candidates?.size ?: -1
            println(
                "LOG[CNV.GenerateRoute][EXTRACT] layer=$layer " +
                    "routeCandidateCount=$count " +
                    "polylines=${result?.polylineCount} merged=${result?.mergedPolylineCount}",
            )
            if (result != null && result.candidates.isNotEmpty()) {
                return LayerImport(layer = layer, candidates = result.candidates)
            }
        }
        return null
    }

    private fun resolveEffectiveConveyorLayer(drawing: Drawing): String {
        val saved = drawing.conveyorLayerName.trim()
        val source = drawing.dwgUri
        val fileLayers = source?.let { peekCadLayers(it) }.orEmpty()
        if (saved.isNotEmpty()) {
            if (fileLayers.isEmpty()) return saved
            fileLayers.firstOrNull { it.equals(saved, ignoreCase = true) }?.let { return it }
            // Stale CONVEYOR (or other missing layer) after DXF without that layer.
            if (saved.equals(DWGConfig.DEFAULT_LAYER_FILTER, ignoreCase = true)) {
                return resolveInitialConveyorLayer(fileLayers)
            }
            return saved
        }
        return resolveInitialConveyorLayer(fileLayers)
    }

    private fun logGenerateRoute(
        selectedLayer: String?,
        extractorLayer: String?,
        candidateCount: Int,
        routeSaved: Boolean,
        drawingId: String?,
        activated: Boolean,
        previewRouteExists: Boolean,
        failure: String?,
    ) {
        println("LOG[CNV.GenerateRoute][SELECTED_LAYER] ${selectedLayer ?: "—"}")
        println("LOG[CNV.GenerateRoute][EXTRACTOR_LAYER] ${extractorLayer ?: "—"}")
        println("LOG[CNV.GenerateRoute][ROUTE_CANDIDATE_COUNT] $candidateCount")
        println("LOG[CNV.GenerateRoute][ROUTE_REPOSITORY_SAVED] $routeSaved")
        println("LOG[CNV.GenerateRoute][DRAWING_ID] ${drawingId ?: "—"}")
        println("LOG[CNV.GenerateRoute][ACTIVATE_ROUTE_FOR_DRAWING] $activated")
        println("LOG[CNV.GenerateRoute][ORIGIN_PREVIEW_ROUTE_EXISTS] $previewRouteExists")
        if (failure != null) {
            println("LOG[CNV.GenerateRoute][FAILURE] $failure")
        }
    }

    private data class LayerImport(
        val layer: String,
        val candidates: List<com.example.cnv.route.RouteCandidate>,
    )

    private fun peekCadLayers(sourcePath: String): List<String> {
        return runCatching {
            val reader = CadReaderFactory.create(sourcePath)
            val doc = reader.open(sourcePath)
            reader.readLayers(doc).map { it.name }.distinct()
        }.getOrDefault(emptyList())
    }

    private fun resolveInitialConveyorLayer(layers: List<String>): String {
        if (layers.isEmpty()) return DWGConfig.DEFAULT_LAYER_FILTER
        layers.firstOrNull { it.equals(DWGConfig.DEFAULT_LAYER_FILTER, ignoreCase = true) }
            ?.let { return it }
        layers.firstOrNull { it.equals("0", ignoreCase = true) }?.let { return it }
        return layers.first()
    }

    fun setOriginForCurrentDrawing(x: Float, y: Float): Boolean {
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        if (!drawing.dwgRegistered) return false
        // Allow re-pick while unlocked so Origin can be corrected (STEP 20-23).
        catalog.drawings.upsert(
            drawing.copy(
                originSet = true,
                originX = x,
                originY = y,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        println(
            "LOG[OriginPick][SAVE]\n" +
                "Origin X=${"%.2f".format(x)}\n" +
                "Origin Y=${"%.2f".format(y)}\n" +
                "Drawing=${drawing.id}",
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
