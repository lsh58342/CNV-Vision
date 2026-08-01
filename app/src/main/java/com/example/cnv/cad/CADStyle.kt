package com.example.cnv.cad

import android.graphics.Paint

/**
 * Reusable stroke/fill sizes and Paint factory for CAD rendering.
 * Paint instances are owned by [CADRenderer] and updated when theme changes.
 */
data class CADStyle(
    val routeStrokePx: Float = 4f,
    val gridStrokePx: Float = 1f,
    val nodeRadiusPx: Float = 7f,
    val branchRadiusPx: Float = 9f,
    val startEndRadiusPx: Float = 10f,
    val positionRadiusPx: Float = 12f,
    val overlayTextSizePx: Float = 28f,
    val debugTextSizePx: Float = 24f,
    val overlayPaddingPx: Float = 12f,
    val minZoom: Float = 0.2f,
    val maxZoom: Float = 12f,
    val zoomStep: Float = 1.25f,
) {
    companion object {
        val DEFAULT: CADStyle = CADStyle()
    }
}

/** Applies theme colors onto existing Paint objects (no allocation). */
fun Paint.applyStroke(color: Int, widthPx: Float) {
    this.color = color
    this.strokeWidth = widthPx
    this.style = Paint.Style.STROKE
    this.isAntiAlias = true
}

fun Paint.applyFill(color: Int) {
    this.color = color
    this.style = Paint.Style.FILL
    this.isAntiAlias = true
}

fun Paint.applyText(color: Int, sizePx: Float) {
    this.color = color
    this.textSize = sizePx
    this.style = Paint.Style.FILL
    this.isAntiAlias = true
}
