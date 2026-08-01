package com.example.cnv.heatmap

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import com.example.cnv.cad.CADCamera

/**
 * Renders Shock HeatCells as gradient overlays. Does not mutate CAD/Route.
 * Optimization: paint/rect reuse, gradient cache, viewport culling.
 */
class HeatMapRenderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    data class FrameStats(
        val renderTimeMs: Double,
        val visibleCellCount: Int,
        val culledFromCount: Int,
    )

    @Volatile
    var lastStats: FrameStats = FrameStats(0.0, 0, 0)
        private set

    fun draw(
        canvas: Canvas,
        cells: List<HeatCell>,
        camera: CADCamera,
        maxShock: Float,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val visibleCells = HeatMapViewportCuller.cull(
            cells = cells,
            camera = camera,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
        )
        val normMax = maxShock.coerceAtLeast(1e-3f)
        var visible = 0
        for (cell in visibleCells) {
            val intensity = cell.intensity
            if (intensity <= 0f) continue
            val left = camera.worldToViewX(cell.worldMinX)
            val top = camera.worldToViewY(cell.worldMinY)
            val right = camera.worldToViewX(cell.worldMaxX)
            val bottom = camera.worldToViewY(cell.worldMaxY)
            visible++
            fillPaint.color = HeatGradientCache.colorForIntensity(intensity, normMax)
            rect.set(left, top, right, bottom)
            canvas.drawRect(rect, fillPaint)
        }
        lastStats = FrameStats(
            renderTimeMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0,
            visibleCellCount = visible,
            culledFromCount = cells.size,
        )
    }
}
