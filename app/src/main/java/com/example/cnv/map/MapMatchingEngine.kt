package com.example.cnv.map

import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.EventDispatcher
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.PositionEvent

/**
 * Subscribes to [FusionEvent] only, updates route progress, publishes [PositionEvent].
 * Does not reference Camera, IMU, Fusion APIs, CAD, or DWG.
 */
class MapMatchingEngine(
    private val routeRepository: RouteRepository,
    private val config: MapConfig = MapConfig.DEFAULT,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
    private val positionEstimator: PositionEstimator = PositionEstimator(config),
) {

    @Volatile
    private var latest: RoutePosition? = null

    private val onFusion: (FusionEvent) -> Unit = { onFusionEvent(it) }

    @Volatile
    private var running: Boolean = false

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
    }

    private fun onFusionEvent(event: FusionEvent) {
        val route = routeRepository.current() ?: return
        val position = positionEstimator.estimate(
            route = route,
            deltaDistanceMm = event.distanceMm,
            timestampNs = event.timestampNs,
            confidence = event.confidence,
        ) ?: return
        latest = position
        eventDispatcher.dispatch(position.toPositionEvent())
        com.example.cnv.core.debug.PipelinePerfMonitor.markPositionPublished()
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
