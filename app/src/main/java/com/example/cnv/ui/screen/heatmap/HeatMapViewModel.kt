package com.example.cnv.ui.screen.heatmap

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.OriginCoordinate
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.heatmap.HeatMapDisplayConfig
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.heatmap.HeatMapViewerLayerFlags
import com.example.cnv.heatmap.HeatMapZoneOverlay
import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.ui.screen.drawing.RouteHighlightHelper

/**
 * HeatMap Viewer ViewModel — loads HeatLayer from Repository only (STEP 15 / 15-4).
 * Does not run HeatMapGenerator or create HeatPoints. History selection via nav args.
 */
class HeatMapViewModel(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
) : ViewModel() {

    data class UiState(
        val drawingName: String = "—",
        val drawingId: String? = null,
        val drawing: Drawing? = null,
        val sessions: List<InspectionSessionSummary> = emptyList(),
        val selectedSessionId: String? = null,
        val selectedSummary: InspectionSessionSummary? = null,
        val displayPoints: List<DrawingHeatPoint> = emptyList(),
        val routePolyline: List<Pair<Double, Double>> = emptyList(),
        val originWorld: Pair<Double, Double>? = null,
        val zones: List<HeatMapZoneOverlay> = emptyList(),
        val highlightedZoneId: String? = null,
        val flags: HeatMapViewerLayerFlags = HeatMapViewerLayerFlags(),
        val displayConfig: HeatMapDisplayConfig = HeatMapDisplayConfig.DEFAULT,
        val heatMapMapper: CoordinateMapper? = null,
        val layerGeneratedAtMs: Long = 0L,
        val emptyMessage: String? = null,
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private val mainHandler = Handler(Looper.getMainLooper())
    private var preferredSessionId: String? = null
    private var loadGeneration = 0

    private val layerListener: (String) -> Unit = { drawingId ->
        mainHandler.post {
            if (_state.value?.drawingId == drawingId) {
                refresh()
            }
        }
    }

    init {
        catalog.heatMaps.addLayerListener(layerListener)
    }

    override fun onCleared() {
        catalog.heatMaps.removeLayerListener(layerListener)
        super.onCleared()
    }

    /** Optional session id from Navigation Argument (History → HeatMap). */
    fun setPreferredSessionId(sessionId: String?) {
        preferredSessionId = sessionId
    }

    fun refresh() {
        val drawing = catalog.drawings.current(CurrentContext.get())
        if (drawing == null) {
            _state.value = UiState(emptyMessage = "No Drawing selected")
            return
        }
        val route = catalog.routes.currentRoute()
        val mapper = catalog.routes.underlying().currentMapper()
        val layer = catalog.heatMaps.loadHeatLayer(drawing.id)
        val layout = route?.let { HeatMapRouteLayout.build(it, worldMapper = mapper) }
        val gen = ++loadGeneration
        catalog.inspections.loadHistorySummariesAsync(drawing.id) { sessions ->
            if (gen != loadGeneration) return@loadHistorySummariesAsync
            val preferred = preferredSessionId
                ?.takeIf { id -> sessions.any { it.sessionId == id } }
            val selectedId = preferred
                ?: _state.value?.selectedSessionId?.takeIf { id ->
                    sessions.any { it.sessionId == id }
                }
                ?: sessions.lastOrNull()?.sessionId

            fun publish(points: List<com.example.cnv.heatmap.DrawingHeatPoint>, summary: com.example.cnv.inspection.InspectionSessionSummary?) {
                val snap = com.example.cnv.inspection.RouteSnapshotCodec
                    .decode(summary?.routeSnapshotJson)
                val sessionRoute = snap?.toRoute()
                val sessionMapper = snap?.toMapper() ?: mapper
                val displayRoute = sessionRoute ?: route
                val displayLayout = sessionRoute?.let {
                    HeatMapRouteLayout.build(it, worldMapper = sessionMapper)
                } ?: layout
                _state.value = UiState(
                    drawingName = drawing.name,
                    drawingId = drawing.id,
                    drawing = drawing,
                    sessions = sessions,
                    selectedSessionId = selectedId,
                    selectedSummary = summary,
                    displayPoints = points,
                    routePolyline = buildRoutePolyline(displayLayout),
                    originWorld = resolveOrigin(drawing, displayRoute, displayLayout),
                    zones = if (displayRoute != null && displayLayout != null) {
                        buildZoneOverlays(drawing.id, displayRoute, displayLayout)
                    } else {
                        emptyList()
                    },
                    highlightedZoneId = _state.value?.highlightedZoneId,
                    flags = _state.value?.flags ?: HeatMapViewerLayerFlags(),
                    displayConfig = HeatMapDisplayConfig.DEFAULT,
                    heatMapMapper = displayLayout?.mapper,
                    layerGeneratedAtMs = layer?.generatedAtMs ?: 0L,
                    emptyMessage = when {
                        layer == null && points.isEmpty() ->
                            "No HeatLayer for this Drawing (run Inspection first)"
                        sessions.isEmpty() -> "No Inspection Session for this Drawing"
                        points.isEmpty() -> "HeatLayer has no points for selected session"
                        else -> null
                    },
                )
            }

            val summary = sessions.firstOrNull { it.sessionId == selectedId }
            val memoryPoints = if (selectedId != null) {
                catalog.heatMaps.loadHeatPointsForSession(drawing.id, selectedId)
            } else {
                layer?.points.orEmpty()
            }
            if (selectedId != null && memoryPoints.isEmpty()) {
                catalog.inspections.loadSessionAsync(selectedId) { persisted ->
                    if (gen != loadGeneration) return@loadSessionAsync
                    val restored = catalog.heatMaps.restoreSessionPoints(
                        selectedId,
                        persisted?.summary?.heatPointsJson.orEmpty(),
                    )
                    publish(restored, persisted?.summary ?: summary)
                }
            } else {
                publish(memoryPoints, summary)
            }
        }
    }

    fun selectSession(sessionId: String) {
        val cur = _state.value ?: return
        if (cur.selectedSessionId == sessionId) return
        val drawingId = cur.drawingId ?: return
        preferredSessionId = sessionId
        val memory = catalog.heatMaps.loadHeatPointsForSession(drawingId, sessionId)
        val summary = cur.sessions.firstOrNull { it.sessionId == sessionId }
        fun apply(points: List<com.example.cnv.heatmap.DrawingHeatPoint>) {
            val snap = com.example.cnv.inspection.RouteSnapshotCodec
                .decode(summary?.routeSnapshotJson)
            val sessionRoute = snap?.toRoute()
            val sessionMapper = snap?.toMapper()
                ?: catalog.routes.underlying().currentMapper()
            val layout = sessionRoute?.let {
                HeatMapRouteLayout.build(it, worldMapper = sessionMapper)
            }
            _state.value = cur.copy(
                selectedSessionId = sessionId,
                selectedSummary = summary,
                displayPoints = points,
                routePolyline = if (layout != null) buildRoutePolyline(layout) else cur.routePolyline,
                emptyMessage = if (points.isEmpty()) {
                    "HeatLayer has no points for selected session"
                } else {
                    null
                },
            )
        }
        if (memory.isNotEmpty()) {
            apply(memory)
        } else {
            catalog.inspections.loadSessionAsync(sessionId) { persisted ->
                val restored = catalog.heatMaps.restoreSessionPoints(
                    sessionId,
                    persisted?.summary?.heatPointsJson.orEmpty(),
                )
                apply(restored)
            }
        }
    }

    fun setFlags(flags: HeatMapViewerLayerFlags) {
        val cur = _state.value ?: return
        _state.value = cur.copy(flags = flags)
    }

    fun toggleHeatMap() = flip { it.copy(heatMap = !it.heatMap) }
    fun toggleRoute() = flip { it.copy(route = !it.route) }
    fun toggleZone() = flip { it.copy(zone = !it.zone) }
    fun toggleOrigin() = flip { it.copy(origin = !it.origin) }
    fun toggleShock() = flip { it.copy(shock = !it.shock) }

    fun highlightZone(zoneId: String?) {
        val cur = _state.value ?: return
        val next = if (cur.highlightedZoneId == zoneId) null else zoneId
        _state.value = cur.copy(highlightedZoneId = next)
    }

    private fun flip(block: (HeatMapViewerLayerFlags) -> HeatMapViewerLayerFlags) {
        val cur = _state.value ?: return
        _state.value = cur.copy(flags = block(cur.flags))
    }

    private fun buildRoutePolyline(layout: HeatMapRouteLayout.LayoutResult?): List<Pair<Double, Double>> {
        if (layout == null) return emptyList()
        val pts = ArrayList<Pair<Double, Double>>()
        for (segId in layout.segmentStartMm.keys) {
            val a = HeatMapRouteLayout.toDrawingCoordinate(layout, segId, 0f) ?: continue
            val b = HeatMapRouteLayout.toDrawingCoordinate(layout, segId, 1f) ?: continue
            if (pts.isEmpty()) pts.add(a.x to a.y)
            pts.add(b.x to b.y)
        }
        return pts
    }

    private fun resolveOrigin(
        drawing: Drawing,
        route: Route?,
        layout: HeatMapRouteLayout.LayoutResult?,
    ): Pair<Double, Double>? =
        OriginCoordinate.resolveWorld(drawing, route, layout)

    private fun buildZoneOverlays(
        drawingId: String,
        route: Route,
        layout: HeatMapRouteLayout.LayoutResult,
    ): List<HeatMapZoneOverlay> {
        val zones = catalog.zones.forDrawing(drawingId)
        return zones.mapNotNull { zone -> toZoneOverlay(zone, route, layout) }
    }

    private fun toZoneOverlay(
        zone: Zone,
        route: Route,
        layout: HeatMapRouteLayout.LayoutResult,
    ): HeatMapZoneOverlay? {
        val segmentIds = RouteHighlightHelper.segmentIdsBetween(route, zone.start, zone.end)
        if (segmentIds.isEmpty()) return null
        val pts = ArrayList<Pair<Double, Double>>()
        for (segId in segmentIds) {
            val a = HeatMapRouteLayout.toDrawingCoordinate(layout, segId, 0f) ?: continue
            val b = HeatMapRouteLayout.toDrawingCoordinate(layout, segId, 1f) ?: continue
            if (pts.isEmpty()) pts.add(a.x to a.y)
            pts.add(b.x to b.y)
        }
        if (pts.isEmpty()) return null
        return HeatMapZoneOverlay(
            zoneId = zone.id,
            name = zone.name,
            colorArgb = zone.colorArgb,
            points = pts,
        )
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HeatMapViewModel::class.java)) {
                return HeatMapViewModel() as T
            }
            error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
