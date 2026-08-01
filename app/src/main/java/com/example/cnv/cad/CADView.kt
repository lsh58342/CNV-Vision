package com.example.cnv.cad

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.cnv.map.Route
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.WorldCoordinate

/**
 * CAD canvas view: pan / zoom / render only. No position or route computation.
 */
class CADView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val style = CADStyle.DEFAULT
    private val renderer = CADRenderer(style)
    private val viewport = CADViewport(CADCamera(style), style)
    val layers = CADLayerState()

    private var theme: CADTheme = CADTheme.dark()
    private var route: Route? = null
    private var mapper: CoordinateMapper? = null
    private var layout: CADRenderer.Layout? = null
    private var currentWorld: WorldCoordinate? = null
    private var overlayModel: CADOverlayModel = CADOverlayModel()

    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isPanning = false

    private var frameCount = 0
    private var fpsWindowStartNs = 0L
    private var currentFps = 0.0

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                viewport.camera.zoomAt(detector.scaleFactor, detector.focusX, detector.focusY)
                invalidate()
                return true
            }
        },
    )

    fun theme(): CADTheme = theme

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
        currentWorld = world
        invalidate()
    }

    fun setOverlay(model: CADOverlayModel) {
        overlayModel = model
        invalidate()
    }

    fun zoomIn() {
        viewport.zoomIn(width / 2f, height / 2f)
        invalidate()
    }

    fun zoomOut() {
        viewport.zoomOut(width / 2f, height / 2f)
        invalidate()
    }

    fun resetView() {
        viewport.resetView()
        invalidate()
    }

    fun fitToRoute() {
        val points = layout?.nodeWorld?.values.orEmpty()
        val bounds = viewport.boundsFromPoints(points)
        viewport.fitToRoute(bounds, width.toFloat(), height.toFloat())
        invalidate()
    }

    fun debugSnapshot(): CADDebugSnapshot {
        val stats = renderer.lastStats
        val pos = currentWorld
        return CADDebugSnapshot(
            fps = currentFps,
            renderTimeMs = stats.renderTimeMs,
            visibleSegmentCount = stats.visibleSegmentCount,
            zoomLevel = viewport.camera.scale,
            currentPositionText = if (pos != null) {
                "x=%.1f y=%.1f".format(pos.x, pos.y)
            } else {
                "—"
            },
            currentRouteName = route?.name ?: "—",
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
        renderer.draw(
            canvas = canvas,
            route = currentRoute,
            layout = currentLayout,
            camera = viewport.camera,
            layers = layers,
            theme = theme,
            currentWorld = currentWorld,
            overlayModel = overlayModel,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPanning = true
                lastPanX = event.x
                lastPanY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPanning && !scaleDetector.isInProgress) {
                    viewport.pan(event.x - lastPanX, event.y - lastPanY)
                    lastPanX = event.x
                    lastPanY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
            }
        }
        return true
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
)
