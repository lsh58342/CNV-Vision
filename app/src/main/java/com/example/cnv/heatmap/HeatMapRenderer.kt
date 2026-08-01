package com.example.cnv.heatmap

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import com.example.cnv.cad.CADCamera

/**
 * Renders Shock HeatCells as gradient overlays. Does not mutate CAD/Route.
 */
class HeatMapRenderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    data class FrameStats(
        val renderTimeMs: Double,
        val visibleCellCount: Int,
    )

    @Volatile
    var lastStats: FrameStats = FrameStats(0.0, 0)
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
        val normMax = maxShock.coerceAtLeast(1e-3f)
        var visible = 0
        for (cell in cells) {
            val intensity = cell.intensity
            if (intensity <= 0f) continue
            val left = camera.worldToViewX(cell.worldMinX)
            val top = camera.worldToViewY(cell.worldMinY)
            val right = camera.worldToViewX(cell.worldMaxX)
            val bottom = camera.worldToViewY(cell.worldMaxY)
            if (right < 0f || bottom < 0f || left > viewWidth || top > viewHeight) continue
            visible++
            val t = (intensity / normMax).coerceIn(0f, 1f)
            fillPaint.color = HeatGradient.colorForNormalized(t)
            rect.set(left, top, right, bottom)
            canvas.drawRect(rect, fillPaint)
        }
        lastStats = FrameStats(
            renderTimeMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0,
            visibleCellCount = visible,
        )
    }
}
