package com.example.cnv.cad

import com.example.cnv.route.WorldCoordinate
import kotlin.math.max
import kotlin.math.min

/**
 * Viewport operations over [CADCamera]: zoom, pan, reset, fit-to-route.
 * No rotation.
 */
class CADViewport(
    val camera: CADCamera = CADCamera(),
    private val style: CADStyle = CADStyle.DEFAULT,
) {

    data class Bounds(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double,
    ) {
        fun isValid(): Boolean = maxX >= minX && maxY >= minY
    }

    fun zoomIn(pivotViewX: Float, pivotViewY: Float) {
        camera.zoomAt(style.zoomStep, pivotViewX, pivotViewY)
    }

    fun zoomOut(pivotViewX: Float, pivotViewY: Float) {
        camera.zoomAt(1f / style.zoomStep, pivotViewX, pivotViewY)
    }

    fun pan(dx: Float, dy: Float) {
        camera.panBy(dx, dy)
    }

    fun resetView() {
        camera.reset()
    }

    fun fitToRoute(bounds: Bounds?, viewWidth: Float, viewHeight: Float, paddingPx: Float = 48f) {
        if (bounds == null || !bounds.isValid() || viewWidth <= 0f || viewHeight <= 0f) {
            resetView()
            return
        }
        val worldW = max(bounds.maxX - bounds.minX, 1.0)
        val worldH = max(bounds.maxY - bounds.minY, 1.0)
        val usableW = max(viewWidth - paddingPx * 2f, 1f)
        val usableH = max(viewHeight - paddingPx * 2f, 1f)
        val scaleX = usableW / worldW.toFloat()
        val scaleY = usableH / worldH.toFloat()
        val scale = min(scaleX, scaleY).coerceIn(style.minZoom, style.maxZoom)
        val offsetX = paddingPx - (bounds.minX * scale).toFloat() +
            (usableW - worldW.toFloat() * scale) * 0.5f
        val offsetY = paddingPx - (bounds.minY * scale).toFloat() +
            (usableH - worldH.toFloat() * scale) * 0.5f
        camera.setTransform(scale, offsetX, offsetY)
    }

    fun boundsFromPoints(points: Collection<WorldCoordinate>): Bounds? {
        if (points.isEmpty()) return null
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (p in points) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
        return Bounds(minX, minY, maxX, maxY)
    }
}
