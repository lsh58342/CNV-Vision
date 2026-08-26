package com.example.cnv.ui.screen.review

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.example.cnv.heatmap.DrawingHeatPoint
import com.example.cnv.heatmap.HeatIntensity
import com.example.cnv.heatmap.HeatMapDisplayConfig
import com.example.cnv.imu.ShockUnits
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
    private var routePolyline: List<Pair<Double, Double>> = emptyList()
    private var criticalOnly: Boolean = false
    private val config = HeatMapDisplayConfig.DEFAULT
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = config.routeColor
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = 28f
    }

    fun setHeatPoints(data: List<DrawingHeatPoint>) {
        setMapData(data, routePolyline, criticalOnly)
    }

    fun setMapData(
        data: List<DrawingHeatPoint>,
        route: List<Pair<Double, Double>> = emptyList(),
        criticalOnly: Boolean = false,
    ) {
        points = if (criticalOnly) {
            data.filter { ShockUnits.isCriticalG(it.shockStrength) }
        } else {
            data
        }
        routePolyline = route
        this.criticalOnly = criticalOnly
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF121212.toInt())
        if (points.isEmpty() && routePolyline.isEmpty()) {
            canvas.drawText(
                if (criticalOnly) "No critical shocks" else "No HeatMap",
                24f,
                height / 2f,
                emptyPaint,
            )
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
        for ((x, y) in routePolyline) {
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        if (!minX.isFinite()) return

        val spanX = max(1e-6, maxX - minX)
        val spanY = max(1e-6, maxY - minY)
        val pad = 16f
        val usableW = (width - pad * 2).coerceAtLeast(1f)
        val usableH = (height - pad * 2).coerceAtLeast(1f)
        val scale = min(usableW / spanX.toFloat(), usableH / spanY.toFloat())
        val ox = pad + (usableW - spanX.toFloat() * scale) / 2f
        val oy = pad + (usableH - spanY.toFloat() * scale) / 2f

        fun mapX(x: Double): Float = ox + ((x - minX) * scale).toFloat()
        fun mapY(y: Double): Float = oy + ((y - minY) * scale).toFloat()

        if (routePolyline.size >= 2) {
            val path = Path()
            routePolyline.forEachIndexed { index, (x, y) ->
                val sx = mapX(x)
                val sy = mapY(y)
                if (index == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
            }
            canvas.drawPath(path, routePaint)
        }

        val radius = max(4f, 6f * (scale / 40f).coerceIn(0.5f, 2f))
        for (p in points) {
            fillPaint.color = if (criticalOnly) {
                config.colorCritical
            } else {
                config.colorForShockG(p.shockStrength)
            }
            val sx = mapX(p.drawingX)
            val sy = mapY(p.drawingY)
            val r = radiusFor(p.intensity, radius) *
                (1f + (p.shockStrength - HeatMapDisplayConfig.DEFAULT.shockEmphasisMinStrength)
                    .coerceAtLeast(0f) * 0.2f)
            canvas.drawCircle(sx, sy, r.coerceIn(4f, 18f), fillPaint)
        }
    }

    private fun radiusFor(intensity: HeatIntensity, base: Float): Float = when (intensity) {
        HeatIntensity.CRITICAL -> base * 1.4f
        HeatIntensity.HIGH -> base * 1.2f
        HeatIntensity.MEDIUM -> base
        HeatIntensity.LOW -> base * 0.85f
    }
}
