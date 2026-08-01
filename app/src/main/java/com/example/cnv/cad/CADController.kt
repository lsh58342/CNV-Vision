package com.example.cnv.cad

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.EventDispatcher
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.map.RoutePosition
import com.example.cnv.map.RouteRepository
import com.example.cnv.route.CoordinateMapper

/**
 * Wires RouteRepository (read-only) + CoordinateMapper + PositionEvent → [CADView].
 * STEP 11-2: optionally hosts [CADInteractionController] for gestures/selection/navigation.
 */
class CADController(
    private val routeRepository: RouteRepository,
    private val cadView: CADView,
    private val mapperProvider: () -> CoordinateMapper?,
    private val debugHud: TextView? = null,
    private val validationErrorProvider: () -> String? = { null },
    private val inspectionStateProvider: () -> String? = { null },
    private val errorSegmentIdsProvider: () -> Set<String> = { emptySet() },
    private val selectionInfoView: TextView? = null,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
    private val refreshIntervalMs: Long = 200L,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    @Volatile
    private var latestPositionEvent: PositionEvent? = null

    @Volatile
    private var positionOverlayText: String? = null

    private val interaction = CADInteractionController(
        cadView = cadView,
        selectionInfoView = selectionInfoView,
        inspectionStateProvider = { inspectionStateProvider() ?: "—" },
        errorSegmentIdsProvider = errorSegmentIdsProvider,
    )

    private val onPosition: (PositionEvent) -> Unit = { event ->
        latestPositionEvent = event
        handler.post { applyPosition(event) }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            refreshRoute()
            validationErrorText = validationErrorProvider()
            inspectionStateText = inspectionStateProvider()
            pushOverlay()
            interaction.onPositionEvent(latestPositionEvent)
            updateDebugHud()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        eventDispatcher.subscribe(PositionEvent::class.java, onPosition)
        refreshRoute()
        cadView.fitToRoute()
        interaction.start()
        handler.post(refreshRunnable)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(refreshRunnable)
        eventDispatcher.unsubscribe(PositionEvent::class.java, onPosition)
        interaction.stop()
    }

    fun zoomIn() = cadView.zoomIn()

    fun zoomOut() = cadView.zoomOut()

    fun resetView() = cadView.resetView()

    fun fitToRoute() = interaction.fitToRoute()

    fun goToCurrentPosition() = interaction.goToCurrentPosition()

    fun goToStart() = interaction.goToStart()

    fun goToEnd() = interaction.goToEnd()

    fun centerCurrentPosition() = interaction.centerCurrentPosition()

    fun search(query: String): Boolean = interaction.search(query)

    fun toggleTheme() = cadView.toggleTheme()

    fun setLayerEnabled(layer: CADLayer, enabled: Boolean) {
        interaction.setLayerEnabled(layer, enabled)
    }

    fun toggleLayer(layer: CADLayer) = interaction.toggleLayer(layer)

    private fun refreshRoute() {
        val route = routeRepository.current()
        val mapper = mapperProvider()
        cadView.setRouteData(route, mapper)
        latestPositionEvent?.let { applyPosition(it) }
    }

    private fun applyPosition(event: PositionEvent) {
        val mapper = mapperProvider() ?: return
        val routePosition = RoutePosition(
            segmentId = event.segmentId,
            nodeId = event.nodeId,
            distanceFromSegmentStart = event.distanceFromSegmentStart,
            progress = event.progress,
            direction = event.direction,
            timestampNs = event.timestampNs,
            confidence = event.confidence,
        )
        val world = mapper.toWorld(routePosition)
        cadView.setCurrentWorldPosition(world)
        positionOverlayText = if (world != null) {
            "Pos seg=${event.segmentId} p=%.2f (%.1f, %.1f)".format(
                event.progress,
                world.x,
                world.y,
            )
        } else {
            "Pos seg=${event.segmentId} p=%.2f".format(event.progress)
        }
        interaction.onPositionEvent(event)
        pushOverlay()
    }

    private fun pushOverlay() {
        cadView.setOverlay(
            CADOverlayModel(
                currentPositionText = positionOverlayText,
                validationErrorText = validationErrorText,
                inspectionStateText = inspectionStateText,
            ),
        )
    }

    @Volatile
    private var validationErrorText: String? = null

    @Volatile
    private var inspectionStateText: String? = null

    private fun updateDebugHud() {
        val hud = debugHud ?: return
        val snap = cadView.debugSnapshot()
        hud.text = buildString {
            append("CAD Debug\n")
            append("FPS: %.1f\n".format(snap.fps))
            append("Render: %.2f ms\n".format(snap.renderTimeMs))
            append("Sel: %d\n".format(snap.selectionCount))
            append("Zoom: %.2f\n".format(snap.zoomLevel))
            append("VP: %.0f,%.0f\n".format(snap.viewportX, snap.viewportY))
            append("Visible Seg: %d\n".format(snap.visibleSegmentCount))
            append("Position: %s\n".format(snap.currentPositionText))
            append("Route: %s".format(snap.currentRouteName))
        }
    }
}
