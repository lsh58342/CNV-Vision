package com.example.cnv.heatmap

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.cad.CADView
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.route.CoordinateMapper

/**
 * Orchestrates Mode → Provider → Timeline/Filter → Processor → Overlay.
 * Renderer never sees Timeline or Filter.
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
    private val layers = HeatMapLayerState()
    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var lastSessionId: String? = null
    private var lastEventCount: Int = -1
    private var lastMode: HeatMapMode = modeController.currentMode()
    private var cachedSourcePoints: List<HeatPoint> = emptyList()
    private var latestStats: HeatStatistics = HeatStatistics.EMPTY
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

    fun layers(): HeatMapLayerState = layers

    fun latestStatistics(): HeatStatistics = latestStats

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

        if (sourceChanged) {
            lastSessionId = sessionId
            lastEventCount = eventCount
            lastMode = mode
            if (session == null || eventCount == 0) {
                cachedSourcePoints = emptyList()
                timelineController.bindDataExtent(emptyList())
                currentProviderName = HeatMapFactory.create(mode, mapperProvider).providerName
                publishEmpty(filterRevision)
                return
            }
            val provider = HeatMapFactory.create(mode, mapperProvider)
            currentProviderName = provider.providerName
            cachedSourcePoints = provider.generateHeatPoints(session)
            timelineController.bindDataExtent(cachedSourcePoints)
        }

        if (cachedSourcePoints.isEmpty()) {
            publishEmpty(filterRevision)
            return
        }

        val provider = HeatMapFactory.create(mode, mapperProvider)
        currentProviderName = provider.providerName
        val filterResult = filterController.apply(
            source = cachedSourcePoints,
            timeline = timelineController.timeline(),
        )
        latestFilterResult = filterResult
        val pointsForRender = provider.fromFilterResult(filterResult)
        val hint = inspectionManager.repository().latest()?.statistics?.totalDistanceMm ?: 0f
        val coveredHint = if (filterResult.sourcePointCount <= 0) {
            0f
        } else {
            hint * (filterResult.visiblePointCount.toFloat() / filterResult.sourcePointCount)
        }
        val result = processor.process(pointsForRender, coveredDistanceMmHint = coveredHint)
        latestStats = result.statistics
        appliedFilterRevision = filterRevision
        overlay.setShockHeatData(result.cells, result.statistics)
    }

    private fun publishEmpty(revision: Int) {
        latestFilterResult = HeatMapFilterResult.EMPTY
        latestStats = HeatStatistics.EMPTY
        appliedFilterRevision = revision
        overlay.setShockHeatData(emptyList(), latestStats)
    }

    private fun updateDebugHud() {
        val hud = debugHud ?: return
        val snap = overlay.debugSnapshot()
        val tl = timelineController.timeline()
        val stats = latestStats
        hud.text = buildString {
            append("HeatMap Filter\n")
            append("Timeline: %s\n".format(tl.summary()))
            append("Filter: %s\n".format(filterController.state().summary()))
            append("Visible HP: %d (src %d)\n".format(
                latestFilterResult.visiblePointCount,
                latestFilterResult.sourcePointCount,
            ))
            append("Visible Cells: %d\n".format(snap.visibleCellCount))
            append("MaxShock: %.2f Avg: %.2f\n".format(stats.maximumShock, stats.averageShock))
            append("Covered: %.0f mm\n".format(stats.coveredDistanceMm))
            append("Render: %.2f ms\n".format(snap.renderTimeMs))
            append("FPS: %.1f".format(snap.fps))
        }
    }
}
