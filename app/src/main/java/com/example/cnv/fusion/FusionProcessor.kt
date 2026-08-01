package com.example.cnv.fusion

import com.example.cnv.core.event.CalibrationEvent
import com.example.cnv.core.event.DistanceEvent
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.ShockEvent
import java.util.ArrayDeque

/**
 * Buffers Distance/Shock events, runs [FusionRuleEngine], persists via [FusionRepository].
 * Does not reference Camera or IMU modules.
 */
class FusionProcessor(
    private val ruleEngine: FusionRuleEngine,
    private val repository: FusionRepository,
    private val config: FusionConfig = FusionConfig.DEFAULT,
    private val onFused: (FusionResult) -> Unit,
    initialCalibrated: Boolean = false,
) {

    private val lock = Any()
    private val distanceBuffer = ArrayDeque<DistanceEvent>()
    private val shockBuffer = ArrayDeque<ShockEvent>()
    @Volatile
    private var calibrated: Boolean = initialCalibrated

    fun onDistance(event: DistanceEvent) {
        synchronized(lock) {
            pruneLocked(event.timestampNs)
            distanceBuffer.addLast(event)
            val match = findBestShockLocked(event.timestampNs)
            if (match != null) {
                shockBuffer.remove(match)
                distanceBuffer.remove(event)
                emitLocked(
                    FusionRuleEngine.MatchInput(
                        distance = event,
                        shock = match,
                        calibrated = calibrated,
                    ),
                )
            } else {
                // Keep latest distance for a future shock within the window.
                while (distanceBuffer.size > BUFFER_CAP) {
                    distanceBuffer.removeFirst()
                }
            }
        }
    }

    fun onShock(event: ShockEvent) {
        synchronized(lock) {
            pruneLocked(event.timestampNs)
            shockBuffer.addLast(event)
            val match = findBestDistanceLocked(event.timestampNs)
            if (match != null) {
                distanceBuffer.remove(match)
                shockBuffer.remove(event)
                emitLocked(
                    FusionRuleEngine.MatchInput(
                        distance = match,
                        shock = event,
                        calibrated = calibrated,
                    ),
                )
            } else {
                while (shockBuffer.size > BUFFER_CAP) {
                    shockBuffer.removeFirst()
                }
            }
        }
    }

    fun onCalibration(event: CalibrationEvent) {
        calibrated = when (event.type) {
            CalibrationEvent.Type.FINISHED -> true
            CalibrationEvent.Type.RESET,
            CalibrationEvent.Type.CANCELLED,
            -> false
            CalibrationEvent.Type.STARTED -> calibrated
        }
    }

    fun clear() {
        synchronized(lock) {
            distanceBuffer.clear()
            shockBuffer.clear()
        }
    }

    private fun emitLocked(input: FusionRuleEngine.MatchInput) {
        val result = ruleEngine.evaluate(input)
        if (result == null) {
            repository.recordRejected()
            return
        }
        repository.record(result)
        onFused(result)
    }

    private fun findBestShockLocked(distanceTs: Long): ShockEvent? {
        var best: ShockEvent? = null
        var bestDelta = Long.MAX_VALUE
        for (shock in shockBuffer) {
            if (!ruleEngine.isWithinTimeWindow(distanceTs, shock.timestampNs)) continue
            val delta = kotlin.math.abs(distanceTs - shock.timestampNs)
            if (delta < bestDelta) {
                bestDelta = delta
                best = shock
            }
        }
        return best
    }

    private fun findBestDistanceLocked(shockTs: Long): DistanceEvent? {
        var best: DistanceEvent? = null
        var bestDelta = Long.MAX_VALUE
        for (distance in distanceBuffer) {
            if (!ruleEngine.isWithinTimeWindow(shockTs, distance.timestampNs)) continue
            val delta = kotlin.math.abs(shockTs - distance.timestampNs)
            if (delta < bestDelta) {
                bestDelta = delta
                best = distance
            }
        }
        return best
    }

    private fun pruneLocked(nowNs: Long) {
        while (distanceBuffer.isNotEmpty() &&
            nowNs - distanceBuffer.first().timestampNs > config.maximumDelayNs
        ) {
            distanceBuffer.removeFirst()
        }
        while (shockBuffer.isNotEmpty() &&
            nowNs - shockBuffer.first().timestampNs > config.maximumDelayNs
        ) {
            shockBuffer.removeFirst()
        }
    }

    companion object {
        private const val BUFFER_CAP = 32
    }
}

/** Maps domain result to bus event. */
fun FusionResult.toFusionEvent(): FusionEvent {
    return FusionEvent(
        timestampNs = timestampNs,
        distanceMm = distance,
        confidence = confidence,
        shockLevel = shockLevel,
        trackingCount = trackingCount,
        peakAcceleration = peakAcceleration,
        eventType = eventType,
        timestampDelayNs = timestampDelayNs,
        distanceConfidence = distanceConfidence,
        shockConfidence = shockConfidence,
        calibrated = calibrated,
    )
}
