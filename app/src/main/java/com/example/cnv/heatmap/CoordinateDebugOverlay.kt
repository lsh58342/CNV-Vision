package com.example.cnv.heatmap

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.cnv.cad.CADCamera

/**
 * Developer-only coordinate validation overlay on CAD (STEP 14-1).
 * Displays mapped points / polyline only — no HeatMap blur or gradient.
 */
class CoordinateDebugOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var snapshot: CoordinateValidationSnapshot? = null
    private var cameraProvider: (() -> CADCamera?)? = null
    private var overlayEnabled = false

    private val eventPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_EVENT
        style = Paint.Style.FILL
    }
    private val shockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_SHOCK
        style = Paint.Style.FILL
    }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CURRENT
        style = Paint.Style.FILL
    }
    private val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_ORIGIN
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_POLYLINE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val directionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CURRENT
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    fun setCameraProvider(provider: () -> CADCamera?) {
        cameraProvider = provider
    }

    fun setOverlayEnabled(enabled: Boolean) {
        overlayEnabled = enabled
        invalidate()
    }

    fun setSnapshot(data: CoordinateValidationSnapshot?) {
        snapshot = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!overlayEnabled) return
        val data = snapshot ?: return
        val camera = cameraProvider?.invoke() ?: return

        // Polyline (inspection path order)
        if (data.polyline.size >= 2) {
            for (i in 1 until data.polyline.size) {
                val (x0, y0) = data.polyline[i - 1]
                val (x1, y1) = data.polyline[i]
                canvas.drawLine(
                    camera.worldToViewX(x0),
                    camera.worldToViewY(y0),
                    camera.worldToViewX(x1),
                    camera.worldToViewY(y1),
                    linePaint,
                )
            }
            // Direction tick on last segment
            val (ax, ay) = data.polyline[data.polyline.size - 2]
            val (bx, by) = data.polyline.last()
            canvas.drawLine(
                camera.worldToViewX(ax),
                camera.worldToViewY(ay),
                camera.worldToViewX(bx),
                camera.worldToViewY(by),
                directionPaint,
            )
        }

        for (p in data.points) {
            val paint = when (p.kind) {
                CoordinateDebugPointKind.EVENT -> eventPaint
                CoordinateDebugPointKind.SHOCK -> shockPaint
                CoordinateDebugPointKind.CURRENT -> currentPaint
                CoordinateDebugPointKind.ORIGIN -> originPaint
            }
            val radius = when (p.kind) {
                CoordinateDebugPointKind.CURRENT -> 10f
                CoordinateDebugPointKind.ORIGIN -> 9f
                CoordinateDebugPointKind.SHOCK -> 8f
                CoordinateDebugPointKind.EVENT -> 6f
            }
            canvas.drawCircle(
                camera.worldToViewX(p.drawingX),
                camera.worldToViewY(p.drawingY),
                radius,
                paint,
            )
        }
    }

    companion object {
        private const val COLOR_EVENT = Color.BLUE
        private const val COLOR_SHOCK = Color.RED
        private const val COLOR_CURRENT = Color.GREEN
        private const val COLOR_ORIGIN = Color.YELLOW
        private const val COLOR_POLYLINE = 0xFF80CBC4.toInt()
    }
}
