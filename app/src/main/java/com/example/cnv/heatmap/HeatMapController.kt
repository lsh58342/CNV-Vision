package com.example.cnv.heatmap

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.cad.CADView
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.route.CoordinateMapper

/**
 * Orchestrates Session → Provider → Timeline/Filter → Cache → Processor → Overlay.
 * Renderer never sees Timeline or Filter. Cache owns render-prep reuse.
 */
class HeatMapController(
    private val inspectionManager: InspectionManager,
    private val overlay: HeatMapOverlay,
    private val cadView: CADView,
    private val mapperProvider: () -> CoordinateMapper?,
    private val debugHud: TextView? = null,
    private val modeController: HeatMapModeController = HeatMapModeController(),
    private val timelineController: HeatMapTimelineController = HeatMapTimelineController(),
    private val filterController: HeatMapFilterController = HeatMapFilterController(),
    private val refreshIntervalMs: Long = 250L,
) {
    private val processor = HeatMapProcessor()
    private val cache = HeatMapCache()
    private val analyzer = HeatMapAnalyzer()
    private val sessionFilter = HeatMapSessionFilter()
    private val layers = HeatMapLayerState()
    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var lastSessionId: String? = null
    private var lastEventCount: Int = -1
    private var lastMode: HeatMapMode = modeController.currentMode()
    private var latestStats: HeatStatistics = HeatStatistics.EMPTY
    private var latestSessionStats: HeatSessionStatistics = HeatSessionStatistics.EMPTY
    private var latestAnalysis: HeatMapAnalysis = HeatMapAnalysis.EMPTY
    private var latestFilterResult: HeatMapFilterResult = HeatMapFilterResult.EMPTY
    private var currentProviderName: String = "ShockHeatProvider"
    private var filterRevision: Int = 0
    private var appliedFilterRevision: Int = -1

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            rebuildIfNeeded()
            overlay.invalidate()
            updateDebugHud()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        overlay.setCameraProvider { cadView.viewport().camera }
        overlay.setOverlayEnabled(layers.isEnabled(HeatMapLayer.SHOCK))
        rebuildIfNeeded(force = true)
        handler.post(refreshRunnable)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(refreshRunnable)
    }

    fun modeController(): HeatMapModeController = modeController

    fun timelineController(): HeatMapTimelineController = timelineController

    fun filterController(): HeatMapFilterController = filterController

    fun sessionFilter(): HeatMapSessionFilter = sessionFilter

    fun cache(): HeatMapCache = cache

    fun layers(): HeatMapLayerState = layers

    fun latestStatistics(): HeatStatistics = latestStats

    fun latestSessionStatistics(): HeatSessionStatistics = latestSessionStats

    fun latestAnalysis(): HeatMapAnalysis = latestAnalysis

    fun latestFilterResult(): HeatMapFilterResult = latestFilterResult

    fun setShockLayerEnabled(enabled: Boolean) {
        layers.setEnabled(HeatMapLayer.SHOCK, enabled)
        overlay.setOverlayEnabled(enabled)
        overlay.invalidate()
    }

    fun toggleShockLayer() {
        layers.toggle(HeatMapLayer.SHOCK)
        overlay.setOverlayEnabled(layers.isEnabled(HeatMapLayer.SHOCK))
        overlay.invalidate()
    }

    fun setMode(mode: HeatMapMode): Boolean {
        val changed = modeController.setMode(mode)
        if (changed) {
            rebuildIfNeeded(force = true)
        }
        return changed
    }

    /** Call after Timeline/Filter UI changes. */
    fun notifyFilterChanged() {
        filterRevision++
        rebuildIfNeeded(force = true)
    }

    fun resetTimelineAndFilter() {
        timelineController.reset()
        filterController.reset()
        filterRevision++
        rebuildIfNeeded(force = true)
    }

    private fun rebuildIfNeeded(force: Boolean = false) {
        val session = inspectionManager.currentSession()
        val sessionId = session?.sessionId
        val eventCount = session?.recorder()?.size() ?: 0
        val mode = modeController.currentMode()
        val sourceChanged = sessionId != lastSessionId ||
            eventCount != lastEventCount ||
            mode != lastMode
        val filterChanged = filterRevision != appliedFilterRevision

        if (!force && !sourceChanged && !filterChanged) {
            return
        }

        // Session is always the top-level HeatMap scope.
        sessionFilter.bind(sessionId)

        if (session == null || eventCount == 0) {
            lastSessionId = sessionId
            lastEventCount = eventCount
            lastMode = mode
            currentProviderName = HeatMapFactory.create(mode, mapperProvider).providerName
            publishEmpty(filterRevision)
            return
        }

        val activeSessionId = session.sessionId
        lastSessionId = activeSessionId
        lastEventCount = eventCount
        lastMode = mode

        val provider = HeatMapFactory.create(mode, mapperProvider)
        currentProviderName = provider.providerName

        var sourcePoints = cache.getSource(activeSessionId, eventCount, mode)
        if (sourcePoints == null) {
            sourcePoints = provider.generateHeatPoints(session)
            cache.putSource(activeSessionId, eventCount, mode, sourcePoints)
        }
        timelineController.bindDataExtent(sourcePoints)

        // Session-first: enforce current session id on filter criteria.
        val baseFilter = filterController.state().toFilter(timelineController.timeline())
        val sessionScopedFilter = baseFilter.copy(sessionId = activeSessionId)
        val filterKey = HeatMapFilterPipeline.cacheKey(sessionScopedFilter)

        val cachedLayer = cache.getFilterLayer(activeSessionId, filterKey)
        if (cachedLayer != null) {
            latestFilterResult = HeatMapFilterResult(
                points = cachedLayer.points,
                filter = sessionScopedFilter,
                sourcePointCount = sourcePoints.size,
            )
            latestStats = cachedLayer.statistics
            latestSessionStats = cachedLayer.sessionStatistics
            latestAnalysis = cachedLayer.analysis
            appliedFilterRevision = filterRevision
            overlay.setShockHeatData(cachedLayer.cells, cachedLayer.statistics)
            return
        }

        val sessionPoints = sessionFilter.apply(sourcePoints)
        val filterResult = HeatMapFilterPipeline.applyOrdered(sessionPoints, sessionScopedFilter)
        latestFilterResult = filterResult
        val pointsForRender = provider.fromFilterResult(filterResult)

        val routeTotalMm = session.routeSnapshot.segments.sumOf { it.lengthMm.toDouble() }.toFloat()
        val coveredHint = if (sourcePoints.isEmpty()) {
            0f
        } else {
            routeTotalMm * (
                filterResult.visiblePointCount.toFloat() / sourcePoints.size.coerceAtLeast(1)
                )
        }

        val result = processor.process(pointsForRender, coveredDistanceMmHint = coveredHint)
        val (sessionStats, analysis) = analyzer.analyze(
            sessionId = activeSessionId,
            points = result.points,
            cells = result.cells,
            coveredDistanceMm = result.statistics.coveredDistanceMm,
            routeTotalDistanceMm = routeTotalMm,
            inspectionDurationMs = session.elapsedMs(),
            sourcePointCount = sourcePoints.size,
        )

        cache.putFilterLayer(
            activeSessionId,
            HeatMapCache.FilterLayer(
                filterKey = filterKey,
                points = result.points,
                cells = result.cells,
                statistics = result.statistics,
                sessionStatistics = sessionStats,
                analysis = analysis,
            ),
        )

        latestStats = result.statistics
        latestSessionStats = sessionStats
        latestAnalysis = analysis
        appliedFilterRevision = filterRevision
        overlay.setShockHeatData(result.cells, result.statistics)
    }

    private fun publishEmpty(revision: Int) {
        latestFilterResult = HeatMapFilterResult.EMPTY
        latestStats = HeatStatistics.EMPTY
        latestSessionStats = HeatSessionStatistics.EMPTY
        latestAnalysis = HeatMapAnalysis.EMPTY
        appliedFilterRevision = revision
        overlay.setShockHeatData(emptyList(), latestStats)
    }

    private fun updateDebugHud() {
        val hud = debugHud ?: return
        val snap = overlay.debugSnapshot()
        val metrics = cache.metrics()
        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0)
        hud.text = buildString {
            append("HeatMap Opt\n")
            append("Session: %s\n".format(sessionFilter.summary()))
            append("Filter: %s\n".format(filterController.state().summary()))
            append("Visible Cells: %d\n".format(snap.visibleCellCount))
            append("Cached Cells: %d\n".format(metrics.cachedCellCount))
            append("Cache Hit: %.0f%% (%d/%d)\n".format(
                metrics.hitRate * 100.0,
                metrics.hits,
                metrics.hits + metrics.misses,
            ))
            append("Analysis: %s\n".format(latestAnalysis.summary()))
            append("Stats: %s\n".format(latestSessionStats.summary()))
            append("Render: %.2f ms\n".format(snap.renderTimeMs))
            append("FPS: %.1f\n".format(snap.fps))
            append("Mem: %.1f MB".format(usedMb))
        }
    }
}
