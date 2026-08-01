package com.example.cnv.inspection

import com.example.cnv.core.event.BaseEvent
import com.example.cnv.core.event.CalibrationEvent
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.core.event.SystemEvent
import kotlin.math.min

/**
 * Append-only event journal. Events are stored as-is (never mutated).
 */
class InspectionRecorder {

    private val lock = Any()
    private val events = mutableListOf<BaseEvent>()

    fun record(event: BaseEvent) {
        synchronized(lock) {
            events.add(event)
        }
    }

    fun clear() {
        synchronized(lock) {
            events.clear()
        }
    }

    fun snapshot(): List<BaseEvent> = synchronized(lock) { events.toList() }

    fun size(): Int = synchronized(lock) { events.size }

    fun computeStatistics(
        freeze: InspectionFreezeSnapshot,
        startTimeMs: Long,
        endTimeMs: Long,
    ): InspectionStatistics {
        val copy = snapshot()
        var totalDistance = 0f
        var shockCount = 0
        var confidenceSum = 0f
        var confidenceSamples = 0
        var maxShock = 0f
        var minConfidence = Float.POSITIVE_INFINITY

        for (event in copy) {
            when (event) {
                is FusionEvent -> {
                    totalDistance += kotlin.math.abs(event.distanceMm)
                    confidenceSum += event.confidence
                    confidenceSamples++
                    minConfidence = min(minConfidence, event.confidence)
                    if (event.shockLevel > 0f) {
                        shockCount++
                        if (event.shockLevel > maxShock) {
                            maxShock = event.shockLevel
                        }
                    }
                }
                is PositionEvent -> {
                    confidenceSum += event.confidence
                    confidenceSamples++
                    minConfidence = min(minConfidence, event.confidence)
                }
                is CalibrationEvent, is SystemEvent -> {
                    // append-only journal; freeze already holds calibration context
                }
            }
        }

        val avgConfidence = if (confidenceSamples == 0) {
            0f
        } else {
            confidenceSum / confidenceSamples
        }
        if (minConfidence == Float.POSITIVE_INFINITY) {
            minConfidence = 0f
        }

        return InspectionStatistics(
            totalDistanceMm = totalDistance,
            inspectionTimeMs = (endTimeMs - startTimeMs).coerceAtLeast(0L),
            shockCount = shockCount,
            averageConfidence = avgConfidence,
            maximumShockLevel = maxShock,
            minimumConfidence = minConfidence,
            totalEvents = copy.size,
            routeVersion = freeze.routeVersion,
            calibrationVersion = freeze.calibrationVersion,
        )
    }
}
