package com.example.cnv.replay.internal

import com.example.cnv.replay.ReplayEngineStatistics
import com.example.cnv.replay.ReplayFrame
import com.example.cnv.inspection.InspectionSessionSummary

/**
 * Provides Replay statistics from cache + current frame (Engine-only).
 */
internal class ReplayStatisticsProvider {

    fun statistics(
        summary: InspectionSessionSummary?,
        frames: List<ReplayFrame>,
        current: ReplayFrame?,
        currentIndex: Int,
    ): ReplayEngineStatistics {
        if (current == null) {
            return ReplayEngineStatistics(
                shockCount = frames.count { it.hasShock },
                coverage = summary?.coverage ?: 0f,
                validationScore = summary?.speedValidation?.validationScore ?: 0f,
            )
        }
        val speed = estimateSpeedMmPerSec(frames, currentIndex)
        return ReplayEngineStatistics(
            currentDistanceMm = current.distanceMm,
            currentSpeedMmPerSec = speed,
            currentConfidence = current.trackingConfidence,
            shockCount = frames.take(currentIndex + 1).count { it.hasShock },
            coverage = summary?.coverage ?: 0f,
            elapsedMs = current.elapsedMs,
            currentTimeMs = if (current.timestampNs > 0L) current.timestampNs / 1_000_000L else 0L,
            routePositionMm = current.routePositionMm,
            hasShock = current.hasShock,
            shockStrength = current.shockStrength,
            currentZoneName = current.zoneName?.takeIf { it.isNotBlank() } ?: "—",
            validationScore = summary?.speedValidation?.validationScore ?: 0f,
            drawingX = current.drawingX,
            drawingY = current.drawingY,
            timestampNs = current.timestampNs,
        )
    }

    private fun estimateSpeedMmPerSec(frames: List<ReplayFrame>, index: Int): Float {
        if (index <= 0 || frames.isEmpty()) return 0f
        val cur = frames.getOrNull(index) ?: return 0f
        val prev = frames.getOrNull(index - 1) ?: return 0f
        val dtSec = ((cur.timestampNs - prev.timestampNs) / 1_000_000_000.0).toFloat()
        if (dtSec <= 0f) return 0f
        val dd = (cur.distanceMm - prev.distanceMm).coerceAtLeast(0f)
        return dd / dtSec
    }
}
