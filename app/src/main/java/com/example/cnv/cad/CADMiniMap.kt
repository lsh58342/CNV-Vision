package com.example.cnv.cad

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.cnv.route.WorldCoordinate

/**
 * Top-right mini-map. Rendering only — mirrors viewport rect over route bounds.
 */
class CADMiniMap(
    private val style: CADStyle = CADStyle.DEFAULT,
) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val viewportPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val positionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val viewRect = RectF()

    var sizePx: Float = 120f
    var marginPx: Float = 12f

    fun draw(
        canvas: Canvas,
        layout: CADRenderer.Layout,
        camera: CADCamera,
        theme: CADTheme,
        currentWorld: WorldCoordinate?,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        if (layout.segmentWorld.isEmpty()) return
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (world in layout.nodeWorld.values) {
            minX = minOf(minX, world.x)
            minY = minOf(minY, world.y)
            maxX = maxOf(maxX, world.x)
            maxY = maxOf(maxY, world.y)
        }
        val worldW = (maxX - minX).coerceAtLeast(1.0)
        val worldH = (maxY - minY).coerceAtLeast(1.0)

        val left = viewWidth - sizePx - marginPx
        val top = marginPx
        rect.set(left, top, left + sizePx, top + sizePx)

        bgPaint.applyFill(theme.overlayBackground)
        borderPaint.applyStroke(theme.debugText, 2f)
        canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)

        val pad = 8f
        val usable = sizePx - pad * 2f
        val scale = minOf(usable / worldW.toFloat(), usable / worldH.toFloat())
        fun mapX(x: Double): Float = left + pad + ((x - minX) * scale).toFloat()
        fun mapY(y: Double): Float = top + pad + ((y - minY) * scale).toFloat()

        routePaint.applyStroke(theme.route, 2f)
        for (pair in layout.segmentWorld.values) {
            canvas.drawLine(
                mapX(pair.first.x),
                mapY(pair.first.y),
                mapX(pair.second.x),
                mapY(pair.second.y),
                routePaint,
            )
        }

        val worldLeft = camera.viewToWorldX(0f)
        val worldTop = camera.viewToWorldY(0f)
        val worldRight = camera.viewToWorldX(viewWidth)
        val worldBottom = camera.viewToWorldY(viewHeight)
        viewRect.set(
            mapX(worldLeft),
            mapY(worldTop),
            mapX(worldRight),
            mapY(worldBottom),
        )
        viewportPaint.applyStroke(theme.currentPosition, 2f)
        canvas.drawRect(viewRect, viewportPaint)

        if (currentWorld != null) {
            positionPaint.applyFill(theme.currentPosition)
            canvas.drawCircle(mapX(currentWorld.x), mapY(currentWorld.y), 4f, positionPaint)
        }
    }
}
