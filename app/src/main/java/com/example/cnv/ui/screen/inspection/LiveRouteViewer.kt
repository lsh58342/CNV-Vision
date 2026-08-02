package com.example.cnv.ui.screen.inspection

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Inspection Live Route overlay — Route segments only (no DXF / Geometry).
 * Marker interpolates toward dashboard coordinates (150ms).
 */
class LiveRouteViewer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val traversedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF2196F3.toInt()
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFEB3B.toInt()
    }
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val markerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFFFFFF.toInt()
    }
    private val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF4CAF50.toInt()
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val arrowPath = Path()

    private var state: LiveRouteOverlayState = LiveRouteOverlayState()
    private var displayX: Double? = null
    private var displayY: Double? = null
    private var animator: ValueAnimator? = null

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun bind(next: LiveRouteOverlayState) {
        state = next
        recomputeTransform()
        val tx = next.markerX
        val ty = next.markerY
        if (tx == null || ty == null) {
            animator?.cancel()
            // Keep last display position after stop (do not clear).
            invalidate()
            return
        }
        val fromX = displayX ?: tx
        val fromY = displayY ?: ty
        if (fromX == tx && fromY == ty) {
            displayX = tx
            displayY = ty
            invalidate()
            return
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MARKER_ANIM_MS
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                displayX = fromX + (tx - fromX) * t
                displayY = fromY + (ty - fromY) * t
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeTransform()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF121212.toInt())
        val segs = state.segments
        if (segs.isEmpty()) return

        // Full route — gray
        routePaint.color = 0xFF9E9E9E.toInt()
        for (seg in segs) {
            canvas.drawLine(
                worldToViewX(seg.startX),
                worldToViewY(seg.startY),
                worldToViewX(seg.endX),
                worldToViewY(seg.endY),
                routePaint,
            )
        }

        // Zone highlight — yellow (under traversed so progress stays visible)
        for (seg in segs) {
            if (seg.id !in state.zoneSegmentIds) continue
            canvas.drawLine(
                worldToViewX(seg.startX),
                worldToViewY(seg.startY),
                worldToViewX(seg.endX),
                worldToViewY(seg.endY),
                zonePaint,
            )
        }

        // Traversed — blue (full completed segments + partial current)
        for (seg in segs) {
            val fullyDone = seg.id in state.traversedSegmentIds &&
                seg.id != state.currentSegmentId
            if (fullyDone) {
                canvas.drawLine(
                    worldToViewX(seg.startX),
                    worldToViewY(seg.startY),
                    worldToViewX(seg.endX),
                    worldToViewY(seg.endY),
                    traversedPaint,
                )
            }
        }
        state.currentSegmentId?.let { curId ->
            val seg = segs.firstOrNull { it.id == curId } ?: return@let
            val t = state.currentProgress.coerceIn(0f, 1f).toDouble()
            val mx = seg.startX + (seg.endX - seg.startX) * t
            val my = seg.startY + (seg.endY - seg.startY) * t
            canvas.drawLine(
                worldToViewX(seg.startX),
                worldToViewY(seg.startY),
                worldToViewX(mx),
                worldToViewY(my),
                traversedPaint,
            )
        }

        // Origin — green
        val ox = state.originX
        val oy = state.originY
        if (ox != null && oy != null) {
            canvas.drawCircle(worldToViewX(ox), worldToViewY(oy), 10f, originPaint)
            canvas.drawCircle(worldToViewX(ox), worldToViewY(oy), 10f, markerStroke)
        }

        // Current marker + direction
        val mx = displayX ?: state.markerX
        val my = displayY ?: state.markerY
        if (mx != null && my != null) {
            val color = trackingColor(state.tracking)
            markerFill.color = color
            arrowPaint.color = color
            val vx = worldToViewX(mx)
            val vy = worldToViewY(my)
            canvas.drawCircle(vx, vy, 12f, markerFill)
            canvas.drawCircle(vx, vy, 12f, markerStroke)
            drawArrow(canvas, vx, vy, state.directionRad)
        }
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, rad: Float) {
        val len = 22f
        val tipX = cx + cos(rad) * len
        val tipY = cy + sin(rad) * len
        val left = rad + 2.6f
        val right = rad - 2.6f
        val base = 10f
        arrowPath.reset()
        arrowPath.moveTo(tipX, tipY)
        arrowPath.lineTo(cx + cos(left) * base, cy + sin(left) * base)
        arrowPath.lineTo(cx + cos(right) * base, cy + sin(right) * base)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
    }

    private fun trackingColor(tracking: LiveTrackingVisual): Int = when (tracking) {
        LiveTrackingVisual.GOOD -> 0xFF4CAF50.toInt()
        LiveTrackingVisual.SEARCHING -> 0xFFFFEB3B.toInt()
        LiveTrackingVisual.LOST -> 0xFFF44336.toInt()
        LiveTrackingVisual.STOPPED -> 0xFF9E9E9E.toInt()
    }

    private fun recomputeTransform() {
        val segs = state.segments
        if (segs.isEmpty() || width <= 0 || height <= 0) {
            scale = 1f
            offsetX = 24f
            offsetY = 24f
            return
        }
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (s in segs) {
            minX = min(minX, min(s.startX, s.endX))
            minY = min(minY, min(s.startY, s.endY))
            maxX = max(maxX, max(s.startX, s.endX))
            maxY = max(maxY, max(s.startY, s.endY))
        }
        val pad = 28f
        val worldW = max(maxX - minX, 1.0)
        val worldH = max(maxY - minY, 1.0)
        val usableW = max(width - pad * 2f, 1f)
        val usableH = max(height - pad * 2f, 1f)
        scale = min(usableW / worldW.toFloat(), usableH / worldH.toFloat())
        offsetX = pad - (minX * scale).toFloat() + (usableW - worldW.toFloat() * scale) * 0.5f
        offsetY = pad - (minY * scale).toFloat() + (usableH - worldH.toFloat() * scale) * 0.5f
    }

    private fun worldToViewX(x: Double): Float = (x * scale).toFloat() + offsetX

    private fun worldToViewY(y: Double): Float = (y * scale).toFloat() + offsetY

    companion object {
        private const val MARKER_ANIM_MS = 150L
    }
}
