package com.example.cnv.ui.screen.replay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.cnv.cad.CADCamera
import com.example.cnv.heatmap.HeatMapZoneOverlay
import com.example.cnv.replay.ReplayFrame
import com.example.cnv.replay.analysis.ReplayHighlightKind

/**
 * Replay Viewer overlay — markers / highlights only (STEP 16-1).
 * Does not run ReplayEngine or Analysis algorithms.
 */
class ReplayOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var cameraProvider: (() -> CADCamera?)? = null
    private var routePolyline: List<Pair<Double, Double>> = emptyList()
    private var zones: List<HeatMapZoneOverlay> = emptyList()
    private var highlightedZoneId: String? = null
    private var current: ReplayFrame? = null
    private var currentKind: ReplayHighlightKind = ReplayHighlightKind.NONE
    private var shockFrames: List<ReplayFrame> = emptyList()
    private var lowConfidenceFrames: List<ReplayFrame> = emptyList()

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0xFF90CAF9.toInt()
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFFFFFF.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 26f
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    fun setCameraProvider(provider: () -> CADCamera?) {
        cameraProvider = provider
    }

    fun setRoutePolyline(points: List<Pair<Double, Double>>) {
        routePolyline = points
        invalidate()
    }

    fun setZones(overlays: List<HeatMapZoneOverlay>) {
        zones = overlays
        invalidate()
    }

    fun setHighlightedZone(zoneId: String?) {
        highlightedZoneId = zoneId
        invalidate()
    }

    fun setCurrent(frame: ReplayFrame?, kind: ReplayHighlightKind) {
        current = frame
        currentKind = kind
        invalidate()
    }

    fun setShockFrames(frames: List<ReplayFrame>) {
        shockFrames = frames
        invalidate()
    }

    fun setLowConfidenceFrames(frames: List<ReplayFrame>) {
        lowConfidenceFrames = frames
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val camera = cameraProvider?.invoke() ?: return

        if (routePolyline.size >= 2) {
            for (i in 0 until routePolyline.lastIndex) {
                val a = routePolyline[i]
                val b = routePolyline[i + 1]
                val ax = camera.worldToViewX(a.first)
                val ay = camera.worldToViewY(a.second)
                val bx = camera.worldToViewX(b.first)
                val by = camera.worldToViewY(b.second)
                canvas.drawLine(ax, ay, bx, by, routePaint)
            }
        }

        for (zone in zones) {
            if (zone.points.size < 2) continue
            val emphasize = zone.zoneId == highlightedZoneId
            zonePaint.color = if (emphasize) 0xFFFFFF00.toInt() else zone.colorArgb
            zonePaint.strokeWidth = if (emphasize) 7f else 4f
            for (i in 0 until zone.points.lastIndex) {
                val a = zone.points[i]
                val b = zone.points[i + 1]
                canvas.drawLine(
                    camera.worldToViewX(a.first),
                    camera.worldToViewY(a.second),
                    camera.worldToViewX(b.first),
                    camera.worldToViewY(b.second),
                    zonePaint,
                )
            }
        }

        for (frame in shockFrames) {
            drawMarker(canvas, camera, frame, COLOR_SHOCK, radius = 10f)
        }
        for (frame in lowConfidenceFrames) {
            drawMarker(canvas, camera, frame, COLOR_LOW_CONF, radius = 8f)
        }

        val cur = current
        if (cur != null) {
            val color = when (currentKind) {
                ReplayHighlightKind.SHOCK -> COLOR_SHOCK
                ReplayHighlightKind.LOW_CONFIDENCE -> COLOR_LOW_CONF
                else -> COLOR_CURRENT
            }
            drawMarker(canvas, camera, cur, color, radius = 14f, label = true)
        }
    }

    private fun drawMarker(
        canvas: Canvas,
        camera: CADCamera,
        frame: ReplayFrame,
        color: Int,
        radius: Float,
        label: Boolean = false,
    ) {
        val x = frame.drawingX ?: return
        val y = frame.drawingY ?: return
        val sx = camera.worldToViewX(x)
        val sy = camera.worldToViewY(y)
        markerPaint.color = color
        canvas.drawCircle(sx, sy, radius, markerPaint)
        canvas.drawCircle(sx, sy, radius, markerStroke)
        if (label) {
            val text = "t=${frame.elapsedMs}ms · ${"%.0f".format(frame.routePositionMm)}mm · conf=${"%.2f".format(frame.trackingConfidence)}"
            canvas.drawText(text, sx + radius + 6f, sy - radius, labelPaint)
        }
    }

    companion object {
        private const val COLOR_CURRENT = 0xFF4CAF50.toInt()
        private const val COLOR_SHOCK = 0xFFE53935.toInt()
        private const val COLOR_LOW_CONF = 0xFFFF9800.toInt()
    }
}
