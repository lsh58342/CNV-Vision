package com.example.cnv.cad

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * HUD overlay drawn on top of CAD canvas.
 * Shows Current Position, Validation Error, Inspection State only (no HeatMap).
 * Renderer must not import Inspection / MapMatching — receives plain strings.
 */
data class CADOverlayModel(
    val currentPositionText: String? = null,
    val validationErrorText: String? = null,
    val inspectionStateText: String? = null,
    /** Drawing-space Route Start Point marker (Commissioning Origin). */
    val originWorldX: Double? = null,
    val originWorldY: Double? = null,
)

class CADOverlay(
    private val style: CADStyle = CADStyle.DEFAULT,
) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val originFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val originStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun draw(
        canvas: Canvas,
        model: CADOverlayModel,
        theme: CADTheme,
        camera: CADCamera? = null,
    ) {
        drawOriginMarker(canvas, model, theme, camera)
        val lines = ArrayList<String>(3)
        model.currentPositionText?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        model.validationErrorText?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        model.inspectionStateText?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        if (lines.isEmpty()) return

        textPaint.applyText(theme.overlayText, style.overlayTextSizePx)
        bgPaint.applyFill(theme.overlayBackground)

        var maxWidth = 0f
        for (line in lines) {
            maxWidth = maxOf(maxWidth, textPaint.measureText(line))
        }
        val lineHeight = textPaint.fontSpacing
        val pad = style.overlayPaddingPx
        val boxH = pad * 2f + lineHeight * lines.size
        val boxW = pad * 2f + maxWidth
        rect.set(pad, pad, pad + boxW, pad + boxH)
        canvas.drawRoundRect(rect, 8f, 8f, bgPaint)

        var y = pad + textPaint.textSize
        for (line in lines) {
            canvas.drawText(line, pad * 2f, y, textPaint)
            y += lineHeight
        }
    }

    private fun drawOriginMarker(
        canvas: Canvas,
        model: CADOverlayModel,
        theme: CADTheme,
        camera: CADCamera?,
    ) {
        val ox = model.originWorldX ?: return
        val oy = model.originWorldY ?: return
        val cam = camera ?: return
        val vx = cam.worldToViewX(ox)
        val vy = cam.worldToViewY(oy)
        originFill.applyFill(theme.startPoint)
        originStroke.applyStroke(theme.overlayText, 4f)
        val r = style.startEndRadiusPx + 10f
        canvas.drawCircle(vx, vy, r, originFill)
        canvas.drawCircle(vx, vy, r + 6f, originStroke)
        canvas.drawCircle(vx, vy, 4f, originStroke)
    }
}
