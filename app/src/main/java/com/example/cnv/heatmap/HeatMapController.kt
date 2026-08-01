package com.example.cnv.heatmap

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.cad.CADView
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.route.CoordinateMapper

/**
 * Drives HeatMap overlay via Mode → Factory → Provider → Processor → Overlay.
 * Does not change [HeatMapRenderer]; mode is invisible to the renderer.
 */
class HeatMapController(
    private val inspectionManager: InspectionManager,
    private val overlay: HeatMapOverlay,
    private val cadView: CADView,
    private val mapperProvider: () -> CoordinateMapper?,
    private val debugHud: TextView? = null,
    private val modeController: HeatMapModeController = HeatMapModeController(),
    private val refreshIntervalMs: Long = 250L,
) {
    private val processor = HeatMapProcessor()
    private val layers = HeatMapLayerState()
    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var lastSessionId: String? = null
    private var lastEventCount: Int = -1
    private var lastMode: HeatMapMode = modeController.currentMode()
    private var latestStats: HeatStatistics = HeatStatistics.EMPTY
    private var currentProviderName: String = "ShockHeatProvider"

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

    fun layers(): HeatMapLayerState = layers

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

    /** Attempts mode change (STEP 12-2: only SHOCK succeeds). */
    fun setMode(mode: HeatMapMode): Boolean {
        val changed = modeController.setMode(mode)
        if (changed) {
            rebuildIfNeeded(force = true)
        }
        return changed
    }

    private fun rebuildIfNeeded(force: Boolean = false) {
        val session = inspectionManager.currentSession()
        val sessionId = session?.sessionId
        val eventCount = session?.recorder()?.size() ?: 0
        val mode = modeController.currentMode()
        if (!force &&
            sessionId == lastSessionId &&
            eventCount == lastEventCount &&
            mode == lastMode
        ) {
            return
        }
        lastSessionId = sessionId
        lastEventCount = eventCount
        lastMode = mode

        if (session == null || eventCount == 0) {
            latestStats = HeatStatistics.EMPTY
            currentProviderName = HeatMapFactory.create(mode, mapperProvider).providerName
            overlay.setShockHeatData(emptyList(), latestStats)
            return
        }

        val provider = HeatMapFactory.create(mode, mapperProvider)
        currentProviderName = provider.providerName
        val points = provider.generateHeatPoints(session)
        val hint = inspectionManager.repository().latest()?.statistics?.totalDistanceMm ?: 0f
        val result = processor.process(points, coveredDistanceMmHint = hint)
        latestStats = result.statistics
        overlay.setShockHeatData(result.cells, result.statistics)
    }

    private fun updateDebugHud() {
        val hud = debugHud ?: return
        val snap = overlay.debugSnapshot()
        hud.text = buildString {
            append("HeatMap Mode\n")
            append("Mode: %s\n".format(modeController.currentMode().name))
            append("Provider: %s\n".format(currentProviderName))
            append("Points: %d\n".format(snap.heatPointCount))
            append("Render: %.2f ms\n".format(snap.renderTimeMs))
            append("FPS: %.1f".format(snap.fps))
        }
    }
}
