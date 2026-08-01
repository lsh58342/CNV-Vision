package com.example.cnv.route

import com.example.cnv.map.RoutePosition
import kotlin.math.hypot

/**
 * Maps [RoutePosition] along stored segment geometry to world / screen.
 * Does not reference CAD or mutate MapMatchingEngine / RouteRepository.
 */
class CoordinateMapper(
    private val segmentGeometry: Map<String, SegmentGeometry>,
    private val config: CoordinateConfig = CoordinateConfig.DEFAULT,
    private val transformer: CoordinateTransformer = CoordinateTransformer(config),
) {

    data class SegmentGeometry(
        val segmentId: String,
        val start: WorldCoordinate,
        val end: WorldCoordinate,
    ) {
        fun length(): Double = hypot(end.x - start.x, end.y - start.y)

        fun pointAtProgress(progress: Float): WorldCoordinate {
            val t = progress.coerceIn(0f, 1f).toDouble()
            return WorldCoordinate(
                x = start.x + (end.x - start.x) * t,
                y = start.y + (end.y - start.y) * t,
            )
        }
    }

    fun toWorld(position: RoutePosition): WorldCoordinate? {
        val geometry = segmentGeometry[position.segmentId] ?: return null
        return geometry.pointAtProgress(position.progress)
    }

    fun toScreen(position: RoutePosition): ScreenCoordinate? {
        val world = toWorld(position) ?: return null
        return transformer.worldToScreen(world)
    }

    fun toScreen(world: WorldCoordinate): ScreenCoordinate {
        return transformer.worldToScreen(world)
    }

    fun segmentIds(): Set<String> = segmentGeometry.keys

    fun coordinateScale(): Double = config.coordinateScale

    fun coordinateOffset(): Pair<Double, Double> = config.offsetX to config.offsetY
}
