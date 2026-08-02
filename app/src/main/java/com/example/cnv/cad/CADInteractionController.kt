package com.example.cnv.cad

import android.widget.TextView
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.map.Route

/**
 * Owns selection + gestures + navigation. Does not compute Route/Position/Fusion.
 */
class CADInteractionController(
    private val cadView: CADView,
    private val selectionInfoView: TextView? = null,
    private val inspectionStateProvider: () -> String = { "—" },
    private val errorSegmentIdsProvider: () -> Set<String> = { emptySet() },
    private val highlightSegmentIdsProvider: () -> Set<String> = { emptySet() },
    private val highlightColorProvider: () -> Int = { SelectionState.DEFAULT_HIGHLIGHT_YELLOW },
) {
    private val selectionManager = SelectionManager()
    private var navigator: CADNavigator? = null
    private var gestureManager: CADGestureManager? = null

    /** Optional UI hook after a successful route hit-test (view coords already converted). */
    var onTapSelection: ((SelectionInfo) -> Unit)? = null

    /** Optional Origin pick hook — receives raw view coordinates for [RouteStartPointPicker]. */
    var onOriginTap: ((viewX: Float, viewY: Float) -> Unit)? = null

    @Volatile
    private var latestPositionEvent: PositionEvent? = null

    @Volatile
    private var latestInfo: SelectionInfo? = null

    private var running = false

    fun start() {
        if (running) return
        running = true
        navigator = CADNavigator(
            viewport = cadView.viewport(),
            invalidate = { cadView.invalidate() },
            viewSize = { cadView.width.toFloat() to cadView.height.toFloat() },
            layoutProvider = { cadView.layoutOrNull() },
            routeProvider = { cadView.routeOrNull() },
            currentWorldProvider = { cadView.currentWorldOrNull() },
        )
        val nav = navigator!!
        gestureManager = CADGestureManager(
            context = cadView.context,
            listener = object : CADGestureManager.Listener {
                override fun onSingleTap(x: Float, y: Float) = handleTap(x, y)
                override fun onDoubleTap(x: Float, y: Float) {
                    nav.zoomAt(1.35f, x, y)
                }
                override fun onLongPress(x: Float, y: Float) = handleTap(x, y)
                override fun onDrag(dx: Float, dy: Float) {
                    nav.panBy(dx, dy)
                }
                override fun onPinch(scaleFactor: Float, focusX: Float, focusY: Float) {
                    nav.zoomAt(scaleFactor, focusX, focusY)
                }
                override fun onFling(vx: Float, vy: Float) {
                    nav.fling(vx, vy)
                }
            },
        )
        cadView.attachInteraction(
            gestureManager = gestureManager!!,
            selectionOverlay = SelectionOverlay(),
            miniMap = CADMiniMap(),
        )
        refreshSelectionVisuals()
    }

    fun stop() {
        if (!running) return
        running = false
        navigator?.cancel()
        navigator = null
        gestureManager = null
        cadView.detachInteraction()
    }

    fun onPositionEvent(event: PositionEvent?) {
        latestPositionEvent = event
        refreshSelectionVisuals()
    }

    fun fitToRoute() {
        navigator?.fitToRoute(true) ?: cadView.fitToRoute()
    }

    fun goToCurrentPosition() = navigator?.goToCurrentPosition(true)

    fun goToStart() = navigator?.goToStart(true)

    fun goToEnd() = navigator?.goToEnd(true)

    fun centerCurrentPosition() = navigator?.centerCurrentPosition(true)

    fun search(query: String): Boolean {
        val ok = navigator?.searchAndGo(query, true) == true
        if (ok) {
            val route = cadView.routeOrNull()
            val layout = cadView.layoutOrNull()
            val q = query.trim()
            when {
                layout?.nodeWorld?.containsKey(q) == true -> selectionManager.selectNode(q)
                route?.nodes?.any { it.id.equals(q, true) } == true -> {
                    val id = route.nodes.first { it.id.equals(q, true) }.id
                    selectionManager.selectNode(id)
                }
                layout?.segmentWorld?.containsKey(q) == true -> selectionManager.selectSegment(q)
                route?.segments?.any { it.id.equals(q, true) } == true -> {
                    val id = route.segments.first { it.id.equals(q, true) }.id
                    selectionManager.selectSegment(id)
                }
            }
            refreshSelectionVisuals()
        }
        return ok
    }

    fun setLayerEnabled(layer: CADLayer, enabled: Boolean) {
        cadView.layers.setEnabled(layer, enabled)
        cadView.invalidate()
    }

    fun toggleLayer(layer: CADLayer) {
        cadView.layers.toggle(layer)
        cadView.invalidate()
    }

    fun selectionCount(): Int = selectionManager.state().selectionCount

    fun latestSelectionInfo(): SelectionInfo? = latestInfo

    private fun handleTap(x: Float, y: Float) {
        onOriginTap?.invoke(x, y)
        val route = cadView.routeOrNull() ?: return
        val layout = cadView.layoutOrNull() ?: return
        selectionManager.setErrorSegments(errorSegmentIdsProvider())
        selectionManager.selectAt(x, y, route, layout, cadView.viewport().camera)
        refreshSelectionVisuals()
        if (selectionManager.state().hasSelection) {
            latestInfo?.let { onTapSelection?.invoke(it) }
        }
    }

    private fun refreshSelectionVisuals() {
        selectionManager.setErrorSegments(errorSegmentIdsProvider())
        selectionManager.setHighlightSegments(
            highlightSegmentIdsProvider(),
            highlightColorProvider(),
        )
        val state = selectionManager.state()
        val world = cadView.currentWorldOrNull()
        val posText = if (world != null) {
            "%.1f, %.1f".format(world.x, world.y)
        } else {
            "—"
        }
        val info = selectionManager.buildInfo(
            route = cadView.routeOrNull(),
            positionEvent = latestPositionEvent,
            currentPositionText = posText,
            inspectionState = inspectionStateProvider(),
        )
        latestInfo = info
        cadView.setSelectionState(state)
        cadView.setSelectionInfo(if (state.hasSelection) info else null)
        selectionInfoView?.text = if (state.hasSelection) {
            info.toDisplayLines().joinToString("\n")
        } else {
            "Selection: none"
        }
        cadView.invalidate()
    }
}
