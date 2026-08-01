package com.example.cnv.route

import com.example.cnv.dwg.Point2d

/**
 * Linear transform helpers between route drawing space, world, and screen.
 * No CAD references.
 */
class CoordinateTransformer(
    private val config: CoordinateConfig = CoordinateConfig.DEFAULT,
) {

    fun pointToWorld(point: Point2d): WorldCoordinate {
        return WorldCoordinate(
            x = point.x * config.coordinateScale + config.offsetX,
            y = point.y * config.coordinateScale + config.offsetY,
        )
    }

    fun worldToScreen(world: WorldCoordinate): ScreenCoordinate {
        return ScreenCoordinate(
            x = (world.x * config.screenScale + config.screenOffsetX).toFloat(),
            y = (world.y * config.screenScale + config.screenOffsetY).toFloat(),
        )
    }

    fun pointToScreen(point: Point2d): ScreenCoordinate {
        return worldToScreen(pointToWorld(point))
    }
}
