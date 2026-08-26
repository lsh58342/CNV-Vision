package com.example.cnv.cad

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate

/**
 * CAD canvas view: pan / zoom / render only. No position or route computation.
 * STEP 11-2 adds optional interaction hooks (gesture / selection / minimap) without domain logic.
 */
class CADView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val style = CADStyle.DEFAULT
    private val renderer = CADRenderer(style)
    private val viewportInternal = CADViewport(CADCamera(style), style)
    val layers = CADLayerState()

    private var theme: CADTheme = CADTheme.dark()
    private var route: Route? = null
    private var mapper: CoordinateMapper? = null
    private var layout: CADRenderer.Layout? = null
    private var currentWorld: WorldCoordinate? = null
    private var displayWorld: WorldCoordinate? = null
    private var overlayModel: CADOverlayModel = CADOverlayModel()
    private var selectionState: SelectionState = SelectionState.EMPTY
    private var selectionInfo: SelectionInfo? = null

    private var gestureManager: CADGestureManager? = null
    private var selectionOverlay: SelectionOverlay? = null
    private var miniMap: CADMiniMap? = null
    private var positionAnimator: ValueAnimator? = null

    private var frameCount = 0
    private var fpsWindowStartNs = 0L
    private var currentFps = 0.0

    fun viewport(): CADViewport = viewportInternal

    fun layoutOrNull(): CADRenderer.Layout? = layout

    fun routeOrNull(): Route? = route

    fun currentWorldOrNull(): WorldCoordinate? = currentWorld

    fun theme(): CADTheme = theme

    fun attachInteraction(
        gestureManager: CADGestureManager,
        selectionOverlay: SelectionOverlay,
        miniMap: CADMiniMap,
    ) {
        this.gestureManager = gestureManager
        this.selectionOverlay = selectionOverlay
        this.miniMap = miniMap
    }

    fun detachInteraction() {
        gestureManager = null
        selectionOverlay = null
        miniMap = null
        selectionState = SelectionState.EMPTY
        selectionInfo = null
    }

    fun setSelectionState(state: SelectionState) {
        selectionState = state
    }

    fun setSelectionInfo(info: SelectionInfo?) {
        selectionInfo = info
    }

    fun setThemeMode(mode: CADThemeMode) {
        theme = CADTheme.of(mode)
        invalidate()
    }

    fun toggleTheme() {
        val next = if (theme.mode == CADThemeMode.DARK) CADThemeMode.LIGHT else CADThemeMode.DARK
        setThemeMode(next)
    }

    fun setRouteData(route: Route?, mapper: CoordinateMapper?) {
        this.route = route
        this.mapper = mapper
        this.layout = route?.let { renderer.buildLayout(it, mapper) }
        invalidate()
    }

    fun setCurrentWorldPosition(world: WorldCoordinate?) {
        val from = displayWorld ?: world
        currentWorld = world
        if (world == null || from == null) {
            displayWorld = world
            invalidate()
            return
        }
        if (from.x == world.x && from.y == world.y) {
            displayWorld = world
            invalidate()
            return
        }
        positionAnimator?.cancel()
        val startX = from.x
        val startY = from.y
        val endX = world.x
        val endY = world.y
        positionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                displayWorld = WorldCoordinate(
                    x = startX + (endX - startX) * t,
                    y = startY + (endY - startY) * t,
                )
                invalidate()
            }
            start()
        }
    }

    fun setOverlay(model: CADOverlayModel) {
        overlayModel = model
        invalidate()
    }

    fun zoomIn() {
        viewportInternal.zoomIn(width / 2f, height / 2f)
        invalidate()
    }

    fun zoomOut() {
        viewportInternal.zoomOut(width / 2f, height / 2f)
        invalidate()
    }

    fun resetView() {
        viewportInternal.resetView()
        invalidate()
    }

    fun fitToRoute() {
        val points = layout?.nodeWorld?.values.orEmpty()
        val bounds = viewportInternal.boundsFromPoints(points)
        viewportInternal.fitToRoute(bounds, width.toFloat(), height.toFloat())
        invalidate()
    }

    /** Fit camera to arbitrary world points (Replay overlay polyline, HeatMap, etc.). */
    fun fitToWorldPoints(points: Collection<Pair<Double, Double>>) {
        if (points.isEmpty()) return
        val worlds = points.map { WorldCoordinate(it.first, it.second) }
        val bounds = viewportInternal.boundsFromPoints(worlds)
        viewportInternal.fitToRoute(bounds, width.toFloat(), height.toFloat())
        invalidate()
    }

    fun debugSnapshot(): CADDebugSnapshot {
        val stats = renderer.lastStats
        val pos = displayWorld ?: currentWorld
        val cam = viewportInternal.camera
        return CADDebugSnapshot(
            fps = currentFps,
            renderTimeMs = stats.renderTimeMs,
            visibleSegmentCount = stats.visibleSegmentCount,
            zoomLevel = cam.scale,
            currentPositionText = if (pos != null) {
                "x=%.1f y=%.1f".format(pos.x, pos.y)
            } else {
                "—"
            },
            currentRouteName = route?.name ?: "—",
            selectionCount = selectionState.selectionCount,
            viewportX = cam.offsetX,
            viewportY = cam.offsetY,
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw == 0 && oldh == 0 && w > 0 && h > 0) {
            fitToRoute()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        trackFps()
        val currentRoute = route
        val currentLayout = layout
        if (currentRoute == null || currentLayout == null) {
            canvas.drawColor(theme.background)
            return
        }
        val camera = viewportInternal.camera
        renderer.draw(
            canvas = canvas,
            route = currentRoute,
            layout = currentLayout,
            camera = camera,
            layers = layers,
            theme = theme,
            currentWorld = displayWorld ?: currentWorld,
            overlayModel = overlayModel,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
        selectionOverlay?.draw(
            canvas = canvas,
            layout = currentLayout,
            camera = camera,
            theme = theme,
            selection = selectionState,
            info = selectionInfo,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
        miniMap?.draw(
            canvas = canvas,
            layout = currentLayout,
            camera = camera,
            theme = theme,
            currentWorld = displayWorld ?: currentWorld,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val gestures = gestureManager
        if (gestures != null) {
            return gestures.onTouchEvent(event)
        }
        // Fallback STEP 11 basic pan/zoom when interaction is not attached.
        return super.onTouchEvent(event)
    }

    private fun trackFps() {
        val now = SystemClock.elapsedRealtimeNanos()
        if (fpsWindowStartNs == 0L) {
            fpsWindowStartNs = now
        }
        frameCount++
        val elapsed = now - fpsWindowStartNs
        if (elapsed >= 1_000_000_000L) {
            currentFps = frameCount * 1_000_000_000.0 / elapsed
            frameCount = 0
            fpsWindowStartNs = now
        }
    }
}

data class CADDebugSnapshot(
    val fps: Double,
    val renderTimeMs: Double,
    val visibleSegmentCount: Int,
    val zoomLevel: Float,
    val currentPositionText: String,
    val currentRouteName: String,
    val selectionCount: Int = 0,
    val viewportX: Float = 0f,
    val viewportY: Float = 0f,
)
