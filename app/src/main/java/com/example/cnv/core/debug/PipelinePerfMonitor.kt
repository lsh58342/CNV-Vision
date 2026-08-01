package com.example.cnv.core.debug

import com.example.cnv.core.common.TimeBase

/**
 * Process-wide frame budget + event latency samples for debug HUD.
 * Thread-safe; does not affect algorithms.
 */
object PipelinePerfMonitor {

    private val lock = Any()

    @Volatile
    private var lastFrameMs: Double = 0.0

    @Volatile
    private var fps: Double = 0.0

    @Volatile
    private var droppedFrames: Long = 0L

    private var frameCountInWindow: Int = 0
    private var windowStartNs: Long = 0L
    private var lastFrameTimestampNs: Long = 0L

    @Volatile
    private var distanceToFusionMs: Double = 0.0

    @Volatile
    private var fusionToPositionMs: Double = 0.0

    @Volatile
    private var distanceToPositionMs: Double = 0.0

    private var lastDistancePublishWallNs: Long = 0L
    private var lastDistanceEventTs: Long = 0L
    private var lastFusionWallNs: Long = 0L

    fun recordFrameProcessed(durationMs: Double, frameTimestampNs: Long) {
        synchronized(lock) {
            lastFrameMs = durationMs
            if (lastFrameTimestampNs > 0L && frameTimestampNs > lastFrameTimestampNs) {
                val gapNs = frameTimestampNs - lastFrameTimestampNs
                // ~30fps nominal; gaps larger than 2 frame intervals count as drops.
                val nominalFrameNs = 33_000_000L
                if (gapNs > nominalFrameNs * 2) {
                    droppedFrames += (gapNs / nominalFrameNs) - 1
                }
            }
            lastFrameTimestampNs = frameTimestampNs

            val now = TimeBase.nowNs()
            if (windowStartNs == 0L) {
                windowStartNs = now
            }
            frameCountInWindow++
            val elapsed = now - windowStartNs
            if (elapsed >= 1_000_000_000L) {
                fps = frameCountInWindow * 1_000_000_000.0 / elapsed
                frameCountInWindow = 0
                windowStartNs = now
            }
        }
    }

    fun recordDropped(count: Long = 1L) {
        if (count <= 0L) return
        synchronized(lock) {
            droppedFrames += count
        }
    }

    fun markDistancePublished(eventTimestampNs: Long) {
        synchronized(lock) {
            lastDistancePublishWallNs = TimeBase.nowNs()
            lastDistanceEventTs = eventTimestampNs
        }
    }

    fun markFusionPublished(eventTimestampNs: Long) {
        val now = TimeBase.nowNs()
        synchronized(lock) {
            if (lastDistancePublishWallNs > 0L) {
                distanceToFusionMs = (now - lastDistancePublishWallNs) / 1_000_000.0
            } else if (lastDistanceEventTs > 0L) {
                distanceToFusionMs = (now - lastDistanceEventTs) / 1_000_000.0
            }
            lastFusionWallNs = now
            // Keep event ts for end-to-end when wall clocks unavailable.
            if (eventTimestampNs > 0L && lastDistanceEventTs > 0L) {
                // no-op: wall-clock path preferred
            }
        }
    }

    fun markPositionPublished() {
        val now = TimeBase.nowNs()
        synchronized(lock) {
            if (lastFusionWallNs > 0L) {
                fusionToPositionMs = (now - lastFusionWallNs) / 1_000_000.0
            }
            if (lastDistancePublishWallNs > 0L) {
                distanceToPositionMs = (now - lastDistancePublishWallNs) / 1_000_000.0
            }
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            frameMs = lastFrameMs,
            fps = fps,
            droppedFrames = droppedFrames,
            distanceToFusionMs = distanceToFusionMs,
            fusionToPositionMs = fusionToPositionMs,
            distanceToPositionMs = distanceToPositionMs,
        )
    }

    data class Snapshot(
        val frameMs: Double,
        val fps: Double,
        val droppedFrames: Long,
        val distanceToFusionMs: Double,
        val fusionToPositionMs: Double,
        val distanceToPositionMs: Double,
    )
}
