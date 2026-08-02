package com.example.cnv.ui.screen.review

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.heatmap.HeatIntensity
import com.example.cnv.heatmap.HeatMapDisplayConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Compact HeatMap preview — displays Repository heat points only (STEP 17-1).
 * Does not generate HeatLayer or run HeatMapGenerator.
 */
class HeatMapPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var points: List<DrawingHeatPoint> = emptyList()
    private val config = HeatMapDisplayConfig.DEFAULT
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = 28f
    }

    fun setHeatPoints(data: List<DrawingHeatPoint>) {
        points = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF121212.toInt())
        if (points.isEmpty()) {
            canvas.drawText("No HeatMap", 24f, height / 2f, emptyPaint)
            return
        }
        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (p in points) {
            minX = min(minX, p.drawingX)
            maxX = max(maxX, p.drawingX)
            minY = min(minY, p.drawingY)
            maxY = max(maxY, p.drawingY)
        }
        val spanX = max(1e-6, maxX - minX)
        val spanY = max(1e-6, maxY - minY)
        val pad = 16f
        val usableW = (width - pad * 2).coerceAtLeast(1f)
        val usableH = (height - pad * 2).coerceAtLeast(1f)
        val scale = min(usableW / spanX.toFloat(), usableH / spanY.toFloat())
        val ox = pad + (usableW - spanX.toFloat() * scale) / 2f
        val oy = pad + (usableH - spanY.toFloat() * scale) / 2f
        val radius = max(4f, 6f * (scale / 40f).coerceIn(0.5f, 2f))
        for (p in points) {
            fillPaint.color = config.colorFor(p.intensity)
            val sx = ox + ((p.drawingX - minX) * scale).toFloat()
            val sy = oy + ((p.drawingY - minY) * scale).toFloat()
            canvas.drawCircle(sx, sy, radiusFor(p.intensity, radius), fillPaint)
        }
    }

    private fun radiusFor(intensity: HeatIntensity, base: Float): Float = when (intensity) {
        HeatIntensity.CRITICAL -> base * 1.4f
        HeatIntensity.HIGH -> base * 1.2f
        HeatIntensity.MEDIUM -> base
        HeatIntensity.LOW -> base * 0.85f
    }
}
