package com.example.cnv.heatmap

/**
 * Viewport-based HeatCell culling. Does not mutate cells.
 */
object HeatMapViewportCuller {

    /**
     * Returns cells whose world AABB intersects the view frustum (with padding).
     */
    fun cull(
        cells: List<HeatCell>,
        camera: com.example.cnv.cad.CADCamera,
        viewWidth: Float,
        viewHeight: Float,
        padPx: Float = 2f,
    ): List<HeatCell> {
        if (cells.isEmpty()) return emptyList()
        val out = ArrayList<HeatCell>(cells.size.coerceAtMost(256))
        val minX = -padPx
        val minY = -padPx
        val maxX = viewWidth + padPx
        val maxY = viewHeight + padPx
        for (cell in cells) {
            val left = camera.worldToViewX(cell.worldMinX)
            val top = camera.worldToViewY(cell.worldMinY)
            val right = camera.worldToViewX(cell.worldMaxX)
            val bottom = camera.worldToViewY(cell.worldMaxY)
            if (right < minX || bottom < minY || left > maxX || top > maxY) continue
            out.add(cell)
        }
        return out
    }
}
