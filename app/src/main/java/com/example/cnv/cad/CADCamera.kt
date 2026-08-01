package com.example.cnv.cad

/**
 * 2D camera for CAD: scale + translation only (no rotation).
 */
class CADCamera(
    private val style: CADStyle = CADStyle.DEFAULT,
) {
    var scale: Float = 1f
        private set
    var offsetX: Float = 40f
        private set
    var offsetY: Float = 40f
        private set

    fun setTransform(scale: Float, offsetX: Float, offsetY: Float) {
        this.scale = scale.coerceIn(style.minZoom, style.maxZoom)
        this.offsetX = offsetX
        this.offsetY = offsetY
    }

    fun worldToViewX(worldX: Double): Float = (worldX * scale).toFloat() + offsetX

    fun worldToViewY(worldY: Double): Float = (worldY * scale).toFloat() + offsetY

    fun viewToWorldX(viewX: Float): Double = ((viewX - offsetX) / scale).toDouble()

    fun viewToWorldY(viewY: Float): Double = ((viewY - offsetY) / scale).toDouble()

    fun zoomAt(factor: Float, pivotViewX: Float, pivotViewY: Float) {
        val worldX = viewToWorldX(pivotViewX)
        val worldY = viewToWorldY(pivotViewY)
        val next = (scale * factor).coerceIn(style.minZoom, style.maxZoom)
        scale = next
        offsetX = pivotViewX - (worldX * scale).toFloat()
        offsetY = pivotViewY - (worldY * scale).toFloat()
    }

    fun panBy(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
    }

    fun reset(defaultScale: Float = 1f, defaultOffsetX: Float = 40f, defaultOffsetY: Float = 40f) {
        scale = defaultScale.coerceIn(style.minZoom, style.maxZoom)
        offsetX = defaultOffsetX
        offsetY = defaultOffsetY
    }
}
