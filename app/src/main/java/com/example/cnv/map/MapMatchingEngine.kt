package com.example.cnv.map

import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.EventDispatcher
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.vio.RouteConstrainedMatcher
import com.example.cnv.vio.VioStateHub

/**
 * Subscribes to [FusionEvent] only, updates route progress, publishes [PositionEvent].
 * Does not reference Camera, IMU, Fusion APIs, CAD, or DWG.
 * VIO heading from [VioStateHub] is used only for corner segment transitions + projection logs.
 */
class MapMatchingEngine(
    private val routeRepository: RouteRepository,
    private val config: MapConfig = MapConfig.DEFAULT,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
    private val positionEstimator: PositionEstimator = PositionEstimator(config),
    private val mapperProvider: () -> CoordinateMapper? = { routeRepository.currentMapper() },
) {

    @Volatile
    private var latest: RoutePosition? = null

    private val onFusion: (FusionEvent) -> Unit = { onFusionEvent(it) }
    private val routeMatcher = RouteConstrainedMatcher()

    @Volatile
    private var running: Boolean = false

    private var layoutCache: HeatMapRouteLayout.LayoutResult? = null
    private var layoutRouteId: String? = null

    fun latestPosition(): RoutePosition? = latest

    fun start() {
        if (running) return
        running = true
        routeRepository.current()?.let { positionEstimator.reset(it) }
        eventDispatcher.subscribe(FusionEvent::class.java, onFusion)
    }

    fun stop() {
        if (!running) return
        running = false
        eventDispatcher.unsubscribe(FusionEvent::class.java, onFusion)
    }

    fun resetTracking() {
        val route = routeRepository.current()
        if (route != null) {
            positionEstimator.reset(route)
        } else {
            positionEstimator.clear()
        }
        latest = null
        layoutCache = null
        layoutRouteId = null
    }

    private fun onFusionEvent(event: FusionEvent) {
        val route = routeRepository.current() ?: return
        val mapper = mapperProvider()
        val deviceHeading = VioStateHub.deviceHeadingDeg
        val current = latest
        val forceNext = if (mapper != null && current != null) {
            routeMatcher.shouldForceNextSegment(
                route = route,
                mapper = mapper,
                currentSegmentId = current.segmentId,
                progress = current.progress,
                deviceHeadingDeg = deviceHeading,
            )
        } else {
            false
        }

        val position = positionEstimator.estimate(
            route = route,
            deltaDistanceMm = event.distanceMm,
            timestampNs = event.timestampNs,
            confidence = event.confidence,
            forceNextSegment = forceNext,
        ) ?: return
        latest = position

        val layout = ensureLayout(route, mapper)
        val absoluteMm = if (layout != null) {
            HeatMapRouteLayout.absoluteRouteMm(layout, position.segmentId, position.progress) ?: 0f
        } else {
            position.distanceFromSegmentStart
        }

        if (mapper != null) {
            val match = routeMatcher.matchFromRoutePosition(
                route = route,
                mapper = mapper,
                position = position,
                deviceHeadingDeg = deviceHeading,
                absoluteRouteMm = absoluteMm,
            )
            if (match != null) {
                VioStateHub.onRouteMatch(
                    segmentId = match.segmentId,
                    routeProgressMm = match.routeProgressMm,
                    projectedX = match.projectedX,
                    projectedY = match.projectedY,
                    distanceToRouteMm = match.distanceToRouteMm,
                    routeHeadingDeg = match.routeHeadingDeg,
                    headingErrorDeg = match.headingErrorDeg,
                )
                println(
                    "LOG[ROUTE_MATCH] seg=${match.segmentId} " +
                        "proj=(${"%.1f".format(match.projectedX)},${"%.1f".format(match.projectedY)}) " +
                        "dist=${"%.1f".format(match.distanceToRouteMm)} " +
                        "routeH=${"%.1f".format(match.routeHeadingDeg)} " +
                        "devH=${"%.1f".format(match.deviceHeadingDeg)} " +
                        "errH=${"%.1f".format(match.headingErrorDeg)}",
                )
                println(
                    "LOG[ROUTE_PROGRESS] mm=${"%.1f".format(match.routeProgressMm)} " +
                        "seg=${match.segmentId} progress=${"%.3f".format(match.segmentProgress)}",
                )
            }
        } else {
            VioStateHub.onRouteMatch(
                segmentId = position.segmentId,
                routeProgressMm = absoluteMm,
                projectedX = 0.0,
                projectedY = 0.0,
                distanceToRouteMm = 0f,
                routeHeadingDeg = 0f,
                headingErrorDeg = 0f,
            )
            println(
                "LOG[ROUTE_PROGRESS] mm=${"%.1f".format(absoluteMm)} " +
                    "seg=${position.segmentId} progress=${"%.3f".format(position.progress)}",
            )
        }

        eventDispatcher.dispatch(position.toPositionEvent())
        com.example.cnv.core.debug.PipelinePerfMonitor.markPositionPublished()
    }

    private fun ensureLayout(
        route: Route,
        mapper: CoordinateMapper?,
    ): HeatMapRouteLayout.LayoutResult? {
        if (layoutCache != null && layoutRouteId == route.id) return layoutCache
        layoutCache = HeatMapRouteLayout.build(route, worldMapper = mapper)
        layoutRouteId = route.id
        return layoutCache
    }
}

fun RoutePosition.toPositionEvent(): PositionEvent {
    return PositionEvent(
        timestampNs = timestampNs,
        segmentId = segmentId,
        nodeId = nodeId,
        distanceFromSegmentStart = distanceFromSegmentStart,
        progress = progress,
        direction = direction,
        confidence = confidence,
    )
}
