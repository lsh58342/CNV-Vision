package com.example.cnv.ui.screen.replay

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.heatmap.HeatMapZoneOverlay
import com.example.cnv.replay.ReplayEngine
import com.example.cnv.replay.ReplayEngineFactory
import com.example.cnv.replay.ReplayEngineStatistics
import com.example.cnv.replay.ReplayFrame
import com.example.cnv.replay.ReplayLoadContext
import com.example.cnv.replay.ReplayPlaybackState
import com.example.cnv.replay.analysis.ReplayAnalysis
import com.example.cnv.replay.analysis.ReplayAnalysisConfig
import com.example.cnv.replay.analysis.ReplayFilter
import com.example.cnv.replay.analysis.ReplayHighlightKind
import com.example.cnv.replay.analysis.ReplayStatistics
import com.example.cnv.ui.screen.drawing.RouteHighlightHelper

/**
 * Replay Viewer ViewModel — Engine API + Analysis / Rule Repositories (STEP 16-3 / 17 / 18).
 * Does not re-analyze Events or re-evaluate Rules; uses cached Results only.
 */
class ReplayViewModel(
    private val engine: ReplayEngine,
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val errorMessage: String? = null,
        val sessionId: String? = null,
        val drawingName: String = "—",
        val playbackState: ReplayPlaybackState = ReplayPlaybackState.IDLE,
        val frameIndex: Int = 0,
        val frameCount: Int = 0,
        val current: ReplayFrame? = null,
        val highlight: ReplayHighlightKind = ReplayHighlightKind.NONE,
        val statistics: ReplayStatistics = ReplayStatistics.EMPTY,
        val engineStatistics: ReplayEngineStatistics = ReplayEngineStatistics.EMPTY,
        val markerLabel: String = "",
        val routePolyline: List<Pair<Double, Double>> = emptyList(),
        val zones: List<HeatMapZoneOverlay> = emptyList(),
        val highlightedZoneId: String? = null,
        val shockFrames: List<ReplayFrame> = emptyList(),
        val lowConfidenceFrames: List<ReplayFrame> = emptyList(),
        val filter: ReplayFilter = ReplayFilter.NONE,
        val searchQuery: String = "",
        val visibleCount: Int = 0,
        val zoneOptions: List<Zone> = emptyList(),
        val sessionStartNs: Long = 0L,
        val playbackSpeed: Float = 1f,
        val routePositionMm: Float = 0f,
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private val analysis = ReplayAnalysis(engine, ReplayAnalysisConfig.DEFAULT)
    private var zones: List<Zone> = emptyList()
    private var routePolyline: List<Pair<Double, Double>> = emptyList()
    private var zoneOverlays: List<HeatMapZoneOverlay> = emptyList()
    private var drawingName: String = "—"
    private var sessionAnalysis: InspectionAnalysisResult? = null
    private var sessionRules: com.example.cnv.rule.InspectionRuleResult? = null

    private val engineListener = ReplayEngine.Listener { publish() }

    init {
        engine.addListener(engineListener)
    }

    override fun onCleared() {
        engine.removeListener(engineListener)
        com.example.cnv.production.ProductionWatchdog.shared().setReplayExpected(false)
        com.example.cnv.production.RecoveryCoordinator.registerReplayReload(null)
        engine.clear()
        super.onCleared()
    }

    fun load(sessionId: String, preferredDrawingId: String?) {
        _state.value = UiState(loading = true)
        val drawing = preferredDrawingId?.let { catalog.drawings.get(it) }
            ?: catalog.drawings.current(CurrentContext.get())
        drawingName = drawing?.name ?: "—"
        val drawingId = preferredDrawingId ?: drawing?.id
        zones = drawingId?.let { catalog.zones.forDrawing(it) }.orEmpty()
        val route = catalog.routes.currentRoute()
        val layout = route?.let { HeatMapRouteLayout.build(it) }
        routePolyline = buildRoutePolyline(layout)
        zoneOverlays = if (drawingId != null && route != null && layout != null) {
            buildZoneOverlays(drawingId, route, layout)
        } else {
            emptyList()
        }

        val loadContext = ReplayLoadContext(
            preferredDrawingId = preferredDrawingId,
            route = route,
            zones = zones,
            layout = layout,
        )
        com.example.cnv.production.RecoveryCoordinator.registerReplayReload {
            (engine as? com.example.cnv.replay.DefaultReplayEngine)?.reloadLastSession()
        }
        val watchdog = com.example.cnv.production.ProductionWatchdog.shared()
        watchdog.setListener(
            object : com.example.cnv.production.ProductionWatchdog.Listener {
                override fun onCameraStall() = Unit
                override fun onSensorStall() = Unit
                override fun onReplayStall() {
                    com.example.cnv.production.RecoveryCoordinator.recoverReplay("stall")
                }
                override fun onFrameProcessingStall() = Unit
            },
        )
        watchdog.setReplayExpected(true)
        watchdog.start()

        engine.loadSession(
            sessionId = sessionId,
            context = loadContext,
        ) { success, error ->
            if (!success) {
                _state.value = UiState(
                    loading = false,
                    errorMessage = error ?: "Load failed",
                )
                return@loadSession
            }
            catalog.analysis.getOrAnalyzeAsync(sessionId, preferredDrawingId) { result ->
                sessionAnalysis = result
                publish()
            }
            catalog.rules.getOrEvaluateAsync(sessionId, preferredDrawingId) { rules ->
                sessionRules = rules
                publish()
            }
            publish()
        }
    }

    fun play() = engine.play()
    fun pause() = engine.pause()
    fun stop() = engine.stop()
    fun restart() = engine.restart()

    fun jumpShockList(): List<ReplayAnalysis.JumpTarget> = analysis.shockTargets()

    fun jumpLowConfidenceList(): List<ReplayAnalysis.JumpTarget> = analysis.lowConfidenceTargets()

    fun jumpZoneList(): List<ReplayAnalysis.ZoneJumpTarget> = analysis.zoneTargets(zones)

    fun selectShock(target: ReplayAnalysis.JumpTarget) {
        analysis.clearZoneHighlight()
        engine.seek(target.frameIndex)
    }

    fun selectZone(target: ReplayAnalysis.ZoneJumpTarget) {
        analysis.setHighlightedZoneId(target.zoneId)
        engine.seek(target.frameIndex)
    }

    fun selectLowConfidence(target: ReplayAnalysis.JumpTarget) {
        analysis.clearZoneHighlight()
        engine.seek(target.frameIndex)
    }

    fun jumpTimestampElapsedMs(elapsedMs: Long) {
        analysis.clearZoneHighlight()
        val ns = analysis.resolveTimestampMs(elapsedMs, treatAsElapsed = true) ?: return
        engine.seekToTimestampNs(ns)
    }

    fun jumpRoutePositionMm(mm: Float) {
        analysis.clearZoneHighlight()
        engine.seekToRoutePositionMm(mm)
    }

    fun previousEvent() {
        analysis.suggestStepVisible(-1)?.let { engine.seek(it) }
    }

    fun nextEvent() {
        analysis.suggestStepVisible(+1)?.let { engine.seek(it) }
    }

    fun previousShock() {
        analysis.clearZoneHighlight()
        analysis.suggestStepMatching(-1) { it.hasShock }?.let { engine.seek(it) }
    }

    fun nextShock() {
        analysis.clearZoneHighlight()
        analysis.suggestStepMatching(+1) { it.hasShock }?.let { engine.seek(it) }
    }

    fun previousZone() {
        analysis.suggestPreviousZoneBoundary(zones)?.let { selectZone(it) }
    }

    fun nextZone() {
        analysis.suggestNextZoneBoundary(zones)?.let { selectZone(it) }
    }

    fun setFilter(filter: ReplayFilter) {
        analysis.setFilter(filter)
        publish()
    }

    fun setSearchQuery(query: String) {
        analysis.setSearchQuery(query)
        publish()
    }

    private fun publish() {
        val frame = engine.currentEvent()
        val live = engine.currentStatistics()
        val analyzed = sessionAnalysis
        // Session-level stats from Analysis Repository; frame cursor stays Engine-owned.
        val engineStats = if (analyzed != null) {
            live.copy(
                shockCount = analyzed.shock.shockCount,
                coverage = analyzed.coverage.routeCoverage,
                validationScore = analyzed.validationScore,
                currentSpeedMmPerSec = live.currentSpeedMmPerSec.takeIf { it > 0f }
                    ?: analyzed.speed.averageSpeedMmPerSec,
            )
        } else {
            live
        }
        val stats = analysis.statistics().let { s ->
            if (analyzed == null) s
            else s.copy(
                validationScore = analyzed.validationScore,
                distanceMm = live.currentDistanceMm.takeIf { it > 0f }
                    ?: analyzed.distance.totalDistanceMm,
            )
        }
        val events = engine.events()
        _state.value = UiState(
            loading = engine.currentState() == ReplayPlaybackState.LOADING,
            errorMessage = engine.errorMessage(),
            sessionId = engine.sessionId(),
            drawingName = drawingName,
            playbackState = engine.currentState(),
            frameIndex = engine.currentIndex(),
            frameCount = engine.frameCount(),
            current = frame,
            highlight = resolveHighlight(frame),
            statistics = stats,
            engineStatistics = engineStats,
            markerLabel = buildMarkerLabel(frame, engineStats),
            routePolyline = routePolyline,
            zones = zoneOverlays,
            highlightedZoneId = analysis.highlightedZoneId(),
            shockFrames = events.filter { it.hasShock },
            lowConfidenceFrames = events.filter {
                it.trackingConfidence in 0f..analysis.lowConfidenceThreshold()
            },
            filter = analysis.filter(),
            searchQuery = analysis.searchQuery(),
            visibleCount = analysis.visibleFrames().size,
            zoneOptions = zones,
            sessionStartNs = events.firstOrNull()?.timestampNs ?: 0L,
            playbackSpeed = engine.playbackSpeed(),
            routePositionMm = engine.currentRoutePositionMm(),
        )
    }

    /**
     * Highlight from cached Rule Result when applicable; otherwise Replay Analysis highlight.
     * Does not re-evaluate rules.
     */
    private fun resolveHighlight(frame: ReplayFrame?): ReplayHighlightKind {
        val base = analysis.currentHighlight()
        val rules = sessionRules ?: return base
        val severe = rules.triggered().any {
            it.severity == com.example.cnv.rule.RuleSeverity.CRITICAL ||
                it.severity == com.example.cnv.rule.RuleSeverity.HIGH
        }
        if (!severe || frame == null) return base
        val shockRule = rules.triggered().any {
            it.category == com.example.cnv.rule.RuleCategory.SHOCK
        }
        val trackRule = rules.triggered().any {
            it.category == com.example.cnv.rule.RuleCategory.TRACKING
        }
        return when {
            shockRule && frame.hasShock -> ReplayHighlightKind.RULE
            trackRule && frame.trackingConfidence in 0f..analysis.lowConfidenceThreshold() ->
                ReplayHighlightKind.RULE
            severe && (base == ReplayHighlightKind.SHOCK || base == ReplayHighlightKind.LOW_CONFIDENCE) ->
                ReplayHighlightKind.RULE
            else -> base
        }
    }

    private fun buildMarkerLabel(frame: ReplayFrame?, stats: ReplayEngineStatistics): String {
        if (frame == null) return "—"
        val xy = if (frame.drawingX != null && frame.drawingY != null) {
            "(${"%.1f".format(frame.drawingX)}, ${"%.1f".format(frame.drawingY)})"
        } else {
            "(—, —)"
        }
        return "t=${stats.elapsedMs}ms · route=${"%.1f".format(stats.routePositionMm)}mm · " +
            "xy=$xy · dist=${"%.1f".format(stats.currentDistanceMm)}mm · " +
            "spd=${"%.1f".format(stats.currentSpeedMmPerSec)} · conf=${"%.2f".format(stats.currentConfidence)}"
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

    private fun buildZoneOverlays(
        drawingId: String,
        route: com.example.cnv.map.Route,
        layout: HeatMapRouteLayout.LayoutResult,
    ): List<HeatMapZoneOverlay> {
        return catalog.zones.forDrawing(drawingId).mapNotNull { zone ->
            val segmentIds = RouteHighlightHelper.segmentIdsBetween(route, zone.start, zone.end)
            if (segmentIds.isEmpty()) return@mapNotNull null
            val pts = ArrayList<Pair<Double, Double>>()
            for (segId in segmentIds) {
                val a = HeatMapRouteLayout.toDrawingCoordinate(layout, segId, 0f) ?: continue
                val b = HeatMapRouteLayout.toDrawingCoordinate(layout, segId, 1f) ?: continue
                if (pts.isEmpty()) pts.add(a.x to a.y)
                pts.add(b.x to b.y)
            }
            if (pts.isEmpty()) return@mapNotNull null
            HeatMapZoneOverlay(
                zoneId = zone.id,
                name = zone.name,
                colorArgb = zone.colorArgb,
                points = pts,
            )
        }
    }

    class Factory(
        private val engine: ReplayEngine = ReplayEngineFactory.createDefault(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReplayViewModel::class.java)) {
                return ReplayViewModel(engine = engine) as T
            }
            error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
