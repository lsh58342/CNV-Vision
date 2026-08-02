package com.example.cnv.inspection

import com.example.cnv.core.event.BaseEvent
import com.example.cnv.core.event.CalibrationEvent
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.core.event.SystemEvent
import kotlin.math.min

/**
 * Bounded in-memory event journal (ring) + optional chunk stream to Room (STEP 20-3).
 * Live stats use running aggregates so ring eviction does not skew [computeStatistics].
 * Full History events live in Room via streamed chunks.
 */
class InspectionRecorder(
    private val maxInMemory: Int = DEFAULT_MAX_IN_MEMORY,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
) {

    private val lock = Any()
    private val ring = ArrayDeque<BaseEvent>()
    private val pendingFlush = ArrayList<BaseEvent>()

    @Volatile
    private var streamTarget: ((List<BaseEvent>) -> Unit)? = null

    // Running aggregates for session statistics (survive ring eviction).
    private var totalEvents = 0
    private var totalDistance = 0f
    private var shockCount = 0
    private var confidenceSum = 0f
    private var confidenceSamples = 0
    private var maxShock = 0f
    private var minConfidence = Float.POSITIVE_INFINITY

    /**
     * Bind Room chunk flush. Invoked off the recorder lock when a chunk is ready.
     */
    fun bindStream(onChunk: (List<BaseEvent>) -> Unit) {
        synchronized(lock) {
            streamTarget = onChunk
        }
    }

    fun record(event: BaseEvent) {
        var chunkToFlush: List<BaseEvent>? = null
        synchronized(lock) {
            ring.addLast(event)
            while (ring.size > maxInMemory) {
                ring.removeFirst()
            }
            accumulate(event)
            pendingFlush.add(event)
            if (pendingFlush.size >= chunkSize) {
                chunkToFlush = pendingFlush.toList()
                pendingFlush.clear()
            }
        }
        chunkToFlush?.let { streamTarget?.invoke(it) }
    }

    /** Flush remaining events not yet streamed (call on session finish). */
    fun flushPending(): List<BaseEvent> {
        val remaining: List<BaseEvent>
        synchronized(lock) {
            remaining = pendingFlush.toList()
            pendingFlush.clear()
        }
        if (remaining.isNotEmpty()) {
            streamTarget?.invoke(remaining)
        }
        return remaining
    }

    fun clear() {
        synchronized(lock) {
            ring.clear()
            pendingFlush.clear()
            streamTarget = null
            totalEvents = 0
            totalDistance = 0f
            shockCount = 0
            confidenceSum = 0f
            confidenceSamples = 0
            maxShock = 0f
            minConfidence = Float.POSITIVE_INFINITY
        }
    }

    /** In-memory ring snapshot (live dashboard / recent window — not full History). */
    fun snapshot(): List<BaseEvent> = synchronized(lock) { ring.toList() }

    fun size(): Int = synchronized(lock) { ring.size }

    fun computeStatistics(
        freeze: InspectionFreezeSnapshot,
        startTimeMs: Long,
        endTimeMs: Long,
    ): InspectionStatistics {
        synchronized(lock) {
            val avgConfidence = if (confidenceSamples == 0) {
                0f
            } else {
                confidenceSum / confidenceSamples
            }
            val minConf = if (minConfidence == Float.POSITIVE_INFINITY) {
                0f
            } else {
                minConfidence
            }
            return InspectionStatistics(
                totalDistanceMm = totalDistance,
                inspectionTimeMs = (endTimeMs - startTimeMs).coerceAtLeast(0L),
                shockCount = shockCount,
                averageConfidence = avgConfidence,
                maximumShockLevel = maxShock,
                minimumConfidence = minConf,
                totalEvents = totalEvents,
                routeVersion = freeze.routeVersion,
                calibrationVersion = freeze.calibrationVersion,
            )
        }
    }

    private fun accumulate(event: BaseEvent) {
        totalEvents++
        when (event) {
            is FusionEvent -> {
                totalDistance += kotlin.math.abs(event.distanceMm)
                confidenceSum += event.confidence
                confidenceSamples++
                minConfidence = min(minConfidence, event.confidence)
                if (event.shockLevel > 0f) {
                    val shockG = com.example.cnv.imu.ShockUnits.ms2ToG(
                        maxOf(event.peakAcceleration, event.shockLevel),
                    )
                    if (com.example.cnv.imu.ShockUnits.isRecordableG(shockG)) {
                        shockCount++
                        if (shockG > maxShock) {
                            maxShock = shockG
                        }
                    }
                }
            }
            is PositionEvent -> {
                confidenceSum += event.confidence
                confidenceSamples++
                minConfidence = min(minConfidence, event.confidence)
            }
            is CalibrationEvent, is SystemEvent -> Unit
        }
    }

    companion object {
        const val DEFAULT_MAX_IN_MEMORY = 12_000
        const val DEFAULT_CHUNK_SIZE = 250
    }
}
