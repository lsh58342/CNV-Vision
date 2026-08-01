package com.example.cnv.fusion

import com.example.cnv.core.debug.PipelinePerfMonitor
import com.example.cnv.core.event.CalibrationEvent
import com.example.cnv.core.event.DistanceEvent
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.ShockEvent
import java.util.ArrayDeque

/**
 * Buffers Shock events, runs [FusionRuleEngine], persists via [FusionRepository].
 * Distance events emit immediately as DISTANCE_ONLY or DISTANCE_AND_SHOCK when a shock
 * is already buffered in the time window (avoids double-counting distance in Map Matching).
 * Does not reference Camera or IMU modules.
 * Event callbacks ([onFused]) are always invoked outside the internal lock.
 */
class FusionProcessor(
    private val ruleEngine: FusionRuleEngine,
    private val repository: FusionRepository,
    private val config: FusionConfig = FusionConfig.DEFAULT,
    private val onFused: (FusionResult) -> Unit,
    initialCalibrated: Boolean = false,
) {

    private val lock = Any()
    private val shockBuffer = ArrayDeque<ShockEvent>()
    @Volatile
    private var calibrated: Boolean = initialCalibrated

    fun onDistance(event: DistanceEvent) {
        val pending = synchronized(lock) {
            pruneLocked(event.timestampNs)
            val match = findBestShockLocked(event.timestampNs)
            if (match != null) {
                shockBuffer.remove(match)
                listOf(
                    FusionRuleEngine.MatchInput(
                        distance = event,
                        shock = match,
                        calibrated = calibrated,
                    ),
                )
            } else {
                listOf(
                    FusionRuleEngine.MatchInput(
                        distance = event,
                        shock = null,
                        calibrated = calibrated,
                    ),
                )
            }
        }
        for (input in pending) {
            emitOutsideLock(input)
        }
    }

    fun onShock(event: ShockEvent) {
        synchronized(lock) {
            pruneLocked(event.timestampNs)
            shockBuffer.addLast(event)
            while (shockBuffer.size > BUFFER_CAP) {
                shockBuffer.removeFirst()
            }
        }
        // Shock-only is not emitted here: Map Matching advances on distance-bearing events.
        // DISTANCE_AND_SHOCK is produced when a later Distance finds this buffered shock.
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
            shockBuffer.clear()
        }
    }

    private fun emitOutsideLock(input: FusionRuleEngine.MatchInput) {
        val result = ruleEngine.evaluate(input)
        if (result == null) {
            repository.recordRejected()
            return
        }
        repository.record(result)
        PipelinePerfMonitor.markFusionPublished(result.timestampNs)
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

    private fun pruneLocked(nowNs: Long) {
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
