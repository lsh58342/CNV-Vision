package com.example.cnv.replay.internal

import com.example.cnv.replay.ReplayFrame
import com.example.cnv.replay.ReplayPosition

/**
 * Timeline cursor — timestamp / route position / progress for the current frame.
 */
internal class ReplayTimelineController {

    @Volatile
    private var index: Int = 0

    @Volatile
    private var frameCount: Int = 0

    @Volatile
    private var current: ReplayFrame? = null

    fun bind(frames: List<ReplayFrame>, startIndex: Int = 0) {
        frameCount = frames.size
        index = if (frames.isEmpty()) 0 else startIndex.coerceIn(0, frames.lastIndex)
        current = frames.getOrNull(index)
    }

    fun clear() {
        index = 0
        frameCount = 0
        current = null
    }

    fun index(): Int = index

    fun frameCount(): Int = frameCount

    fun currentFrame(): ReplayFrame? = current

    fun seek(frames: List<ReplayFrame>, target: Int): ReplayFrame? {
        if (frames.isEmpty()) {
            clear()
            return null
        }
        index = target.coerceIn(0, frames.lastIndex)
        current = frames[index]
        return current
    }

    fun position(): ReplayPosition {
        val frame = current ?: return ReplayPosition(frameCount = frameCount)
        val progress = if (frameCount <= 1) {
            0f
        } else {
            index.toFloat() / (frameCount - 1).toFloat()
        }
        return ReplayPosition(
            frameIndex = index,
            frameCount = frameCount,
            timestampNs = frame.timestampNs,
            elapsedMs = frame.elapsedMs,
            routePositionMm = frame.routePositionMm,
            progress01 = progress,
            drawingX = frame.drawingX,
            drawingY = frame.drawingY,
        )
    }
}
