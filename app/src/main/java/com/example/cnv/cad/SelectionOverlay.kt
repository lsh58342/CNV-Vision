package com.example.cnv.cad

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Draws selection highlights + selection info text. Paint-reused; no Route mutation.
 */
class SelectionOverlay(
    private val style: CADStyle = CADStyle.DEFAULT,
) {
    private val highlightStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val errorStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun draw(
        canvas: Canvas,
        layout: CADRenderer.Layout,
        camera: CADCamera,
        theme: CADTheme,
        selection: SelectionState,
        info: SelectionInfo?,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        errorStroke.applyStroke(theme.validationError, style.routeStrokePx + 4f)
        for (segId in selection.errorSegmentIds) {
            val pair = layout.segmentWorld[segId] ?: continue
            canvas.drawLine(
                camera.worldToViewX(pair.first.x),
                camera.worldToViewY(pair.first.y),
                camera.worldToViewX(pair.second.x),
                camera.worldToViewY(pair.second.y),
                errorStroke,
            )
        }

        highlightStroke.applyStroke(selection.highlightColorArgb, style.routeStrokePx + 8f)
        for (segId in selection.highlightSegmentIds) {
            val pair = layout.segmentWorld[segId] ?: continue
            canvas.drawLine(
                camera.worldToViewX(pair.first.x),
                camera.worldToViewY(pair.first.y),
                camera.worldToViewX(pair.second.x),
                camera.worldToViewY(pair.second.y),
                highlightStroke,
            )
        }

        selection.selectedSegmentId?.let { id ->
            val pair = layout.segmentWorld[id] ?: return@let
            highlightStroke.applyStroke(theme.currentPosition, style.routeStrokePx + 6f)
            canvas.drawLine(
                camera.worldToViewX(pair.first.x),
                camera.worldToViewY(pair.first.y),
                camera.worldToViewX(pair.second.x),
                camera.worldToViewY(pair.second.y),
                highlightStroke,
            )
        }

        val nodeId = selection.selectedBranchNodeId ?: selection.selectedNodeId
        if (nodeId != null) {
            val world = layout.nodeWorld[nodeId]
            if (world != null) {
                highlightFill.applyFill(theme.currentPosition)
                val radius = if (selection.selectedBranchNodeId != null) {
                    style.branchRadiusPx + 6f
                } else {
                    style.nodeRadiusPx + 6f
                }
                canvas.drawCircle(
                    camera.worldToViewX(world.x),
                    camera.worldToViewY(world.y),
                    radius,
                    highlightFill,
                )
            }
        }

        if (info != null && selection.hasSelection) {
            drawInfoPanel(canvas, info, theme, viewWidth, viewHeight)
        }
    }

    private fun drawInfoPanel(
        canvas: Canvas,
        info: SelectionInfo,
        theme: CADTheme,
        viewWidth: Float,
        viewHeight: Float,
    ) {
        textPaint.applyText(theme.overlayText, style.debugTextSizePx)
        bgPaint.applyFill(theme.overlayBackground)
        val lines = info.toDisplayLines()
        var maxW = 0f
        for (line in lines) {
            maxW = maxOf(maxW, textPaint.measureText(line))
        }
        val pad = style.overlayPaddingPx
        val lineH = textPaint.fontSpacing
        val boxW = maxW + pad * 2f
        val boxH = lineH * lines.size + pad * 2f
        val left = viewWidth - boxW - pad
        val top = viewHeight - boxH - pad
        rect.set(left, top, left + boxW, top + boxH)
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
        var y = top + pad + textPaint.textSize
        for (line in lines) {
            canvas.drawText(line, left + pad, y, textPaint)
            y += lineH
        }
    }
}
