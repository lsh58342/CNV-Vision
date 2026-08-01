package com.example.cnv.fusion

import com.example.cnv.core.event.FusionEventType
import java.util.ArrayDeque

/**
 * Latest fusion snapshot, bounded history, and rolling statistics.
 */
class FusionRepository(
    private val historyCapacity: Int = FusionConfig.DEFAULT_HISTORY_CAPACITY,
) {

    private val lock = Any()
    private var latest: FusionResult? = null
    private val history = ArrayDeque<FusionResult>()
    private var stats = FusionStatistics()

    fun latest(): FusionResult? = synchronized(lock) { latest }

    fun history(): List<FusionResult> = synchronized(lock) { history.toList() }

    fun statistics(): FusionStatistics = synchronized(lock) { stats }

    fun record(result: FusionResult) {
        synchronized(lock) {
            latest = result
            history.addLast(result)
            while (history.size > historyCapacity) {
                history.removeFirst()
            }
            stats = updateStats(stats, result)
        }
    }

    fun recordRejected() {
        synchronized(lock) {
            stats = stats.copy(rejectedCount = stats.rejectedCount + 1)
        }
    }

    fun clear() {
        synchronized(lock) {
            latest = null
            history.clear()
            stats = FusionStatistics()
        }
    }

    private fun updateStats(prev: FusionStatistics, result: FusionResult): FusionStatistics {
        val typeCountIncrement = when (result.eventType) {
            FusionEventType.DISTANCE_AND_SHOCK -> prev.copy(fusedCount = prev.fusedCount + 1)
            FusionEventType.DISTANCE_ONLY -> prev.copy(distanceOnlyCount = prev.distanceOnlyCount + 1)
            FusionEventType.SHOCK_ONLY -> prev.copy(shockOnlyCount = prev.shockOnlyCount + 1)
        }
        val accepted =
            typeCountIncrement.fusedCount +
                typeCountIncrement.distanceOnlyCount +
                typeCountIncrement.shockOnlyCount
        val avgConfidence = if (accepted <= 0L) {
            0f
        } else {
            ((prev.averageConfidence * (accepted - 1)) + result.confidence) / accepted
        }
        val avgDelay = if (accepted <= 0L) {
            0L
        } else {
            ((prev.averageTimestampDelayNs * (accepted - 1)) + result.timestampDelayNs) / accepted
        }
        return typeCountIncrement.copy(
            averageConfidence = avgConfidence,
            averageTimestampDelayNs = avgDelay,
            lastTimestampNs = result.timestampNs,
            rejectedCount = prev.rejectedCount,
        )
    }
}
