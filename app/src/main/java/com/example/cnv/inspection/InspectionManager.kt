package com.example.cnv.inspection

import com.example.cnv.core.common.TimeBase
import com.example.cnv.core.event.CalibrationEvent
import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.EventDispatcher
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.core.event.SystemEvent
import com.example.cnv.map.Route
import java.util.UUID

/**
 * Sole creator of [InspectionSession]. Freezes Route/Calibration/config and records events.
 * Does not mutate Route, Events, or re-run validation.
 */
class InspectionManager(
    private val routeCache: RouteCache = RouteCache(),
    private val repository: InspectionRepository = InspectionRepository(),
    private val config: InspectionConfig = InspectionConfig.DEFAULT,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
) {

    data class StartRequest(
        val route: Route,
        val calibrationVersion: Int,
        val calibrationValue: Float,
        val appVersion: String,
        val deviceInformation: String,
        val samplingRateHz: Float = InspectionConfig.DEFAULT_SAMPLING_RATE_HZ,
        /** Precomputed STEP 10-3 score — not recalculated here. */
        val routeQualityScore: Float,
    )

    @Volatile
    private var session: InspectionSession? = null

    private val onPosition: (PositionEvent) -> Unit = { session?.recorder()?.record(it) }
    private val onFusion: (FusionEvent) -> Unit = { session?.recorder()?.record(it) }
    private val onCalibration: (CalibrationEvent) -> Unit = { session?.recorder()?.record(it) }
    private val onSystem: (SystemEvent) -> Unit = { session?.recorder()?.record(it) }

    fun routeCache(): RouteCache = routeCache

    fun repository(): InspectionRepository = repository

    fun state(): InspectionState = session?.state ?: InspectionState.IDLE

    fun currentSession(): InspectionSession? = session

    fun start(request: StartRequest): InspectionSession {
        if (session?.state == InspectionState.RUNNING) {
            stop()
        }
        val now = System.currentTimeMillis()
        val snapshot = RouteSnapshot.from(request.route, capturedAtMs = now)
        routeCache.put(snapshot)
        val freeze = InspectionFreezeSnapshot(
            routeVersion = snapshot.routeVersion,
            routeHash = snapshot.routeHash,
            calibrationVersion = request.calibrationVersion,
            calibrationValue = request.calibrationValue,
            appVersion = request.appVersion,
            timestampMs = now,
            deviceInformation = request.deviceInformation,
            samplingRateHz = request.samplingRateHz,
            routeQualityScore = request.routeQualityScore,
        )
        val created = InspectionSession(
            sessionId = UUID.randomUUID().toString(),
            freeze = freeze,
            routeSnapshot = snapshot,
            startTimeMs = now,
        )
        session = created
        subscribe()
        eventDispatcher.dispatch(
            SystemEvent(
                timestampNs = TimeBase.nowNs(),
                type = SystemEvent.Type.FEATURE_STARTED,
                message = "inspection:${created.sessionId}",
            ),
        )
        return created
    }

    fun stop(): InspectionResult? {
        val active = session ?: return null
        if (active.state != InspectionState.RUNNING) {
            return repository.latest()
        }
        unsubscribe()
        val endMs = System.currentTimeMillis()
        active.markStopped()
        val result = active.buildResult(endMs)
        if (config.autoSave) {
            repository.save(result)
        }
        eventDispatcher.dispatch(
            SystemEvent(
                timestampNs = TimeBase.nowNs(),
                type = SystemEvent.Type.FEATURE_STOPPED,
                message = "inspection:${active.sessionId}",
            ),
        )
        return result
    }

    fun isTimedOut(nowMs: Long = System.currentTimeMillis()): Boolean {
        val active = session ?: return false
        if (active.state != InspectionState.RUNNING) return false
        val elapsed = active.elapsedMs(nowMs)
        return elapsed >= config.sessionTimeoutMs || elapsed >= config.maximumSessionLengthMs
    }

    private fun subscribe() {
        eventDispatcher.subscribe(PositionEvent::class.java, onPosition)
        eventDispatcher.subscribe(FusionEvent::class.java, onFusion)
        eventDispatcher.subscribe(CalibrationEvent::class.java, onCalibration)
        eventDispatcher.subscribe(SystemEvent::class.java, onSystem)
    }

    private fun unsubscribe() {
        eventDispatcher.unsubscribe(PositionEvent::class.java, onPosition)
        eventDispatcher.unsubscribe(FusionEvent::class.java, onFusion)
        eventDispatcher.unsubscribe(CalibrationEvent::class.java, onCalibration)
        eventDispatcher.unsubscribe(SystemEvent::class.java, onSystem)
    }
}
