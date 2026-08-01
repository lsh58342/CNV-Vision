package com.example.cnv.debug

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.cnv.map.Route
import com.example.cnv.map.RoutePosition
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.ValidationIssue
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive black-canvas route debug view (pan / zoom / selection). No CAD.
 */
class RouteDebugView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val renderer = RouteDebugRenderer()
    private val config = RouteDebugConfig.DEFAULT

    private var route: Route? = null
    private var mapper: CoordinateMapper? = null
    private var layout: RouteDebugRenderer.Layout? = null
    private var issues: List<ValidationIssue> = emptyList()
    private var currentPosition: RoutePosition? = null
    private var selectedNodeId: String? = null
    private var selectedSegmentId: String? = null

    private var scale = 1f
    private var offsetX = 40f
    private var offsetY = 40f

    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isPanning = false

    var selectionListener: ((nodeId: String?, segmentId: String?) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                scale = (scale * factor).coerceIn(config.minZoom, config.maxZoom)
                invalidate()
                return true
            }
        },
    )

    fun setRouteData(
        route: Route?,
        mapper: CoordinateMapper?,
        issues: List<ValidationIssue>,
        currentPosition: RoutePosition?,
    ) {
        this.route = route
        this.mapper = mapper
        this.issues = issues
        this.currentPosition = currentPosition
        this.layout = route?.let { renderer.buildLayout(it, mapper) }
        invalidate()
    }

    fun selectIssue(nodeId: String?, segmentId: String?) {
        selectedNodeId = nodeId
        selectedSegmentId = segmentId
        selectionListener?.invoke(nodeId, segmentId)
        invalidate()
    }

    fun selectedNodeId(): String? = selectedNodeId

    fun selectedSegmentId(): String? = selectedSegmentId

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentRoute = route
        val currentLayout = layout
        if (currentRoute == null || currentLayout == null) {
            canvas.drawColor(android.graphics.Color.BLACK)
            return
        }
        renderer.draw(
            canvas = canvas,
            route = currentRoute,
            layout = currentLayout,
            issues = issues,
            selectedNodeId = selectedNodeId,
            selectedSegmentId = selectedSegmentId,
            currentPosition = currentPosition,
            mapper = mapper,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPanning = true
                lastPanX = event.x
                lastPanY = event.y
                handleTap(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPanning && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    offsetX += event.x - lastPanX
                    offsetY += event.y - lastPanY
                    lastPanX = event.x
                    lastPanY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        val currentLayout = layout ?: return
        val nodeId = renderer.hitTestNode(currentLayout, x, y, scale, offsetX, offsetY)
        val segmentId = if (nodeId == null) {
            renderer.hitTestSegment(currentLayout, x, y, scale, offsetX, offsetY)
        } else {
            null
        }
        selectedNodeId = nodeId
        selectedSegmentId = segmentId
        selectionListener?.invoke(nodeId, segmentId)
        invalidate()
    }

    fun zoomIn() {
        scale = min(config.maxZoom, scale * 1.25f)
        invalidate()
    }

    fun zoomOut() {
        scale = max(config.minZoom, scale / 1.25f)
        invalidate()
    }
}
