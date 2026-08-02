package com.example.cnv.replay.internal

import android.os.Handler
import android.os.Looper
import com.example.cnv.replay.ReplayFrame

/**
 * Playback control — Play / Pause / Stop / Seek / Speed.
 * Advances frames on the main Handler using inter-frame timestamps.
 */
internal class ReplayPlaybackController(
    private val onTick: () -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var speed: Float = 1f

    @Volatile
    private var playing: Boolean = false

    private val advanceRunnable = object : Runnable {
        override fun run() {
            if (!playing) return
            onTick()
            if (playing) {
                // Next delay is scheduled by Facade after tick updates timeline.
            }
        }
    }

    fun isPlaying(): Boolean = playing

    fun playbackSpeed(): Float = speed

    fun setPlaybackSpeed(value: Float) {
        speed = value.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    fun play() {
        playing = true
    }

    fun pause() {
        playing = false
        handler.removeCallbacks(advanceRunnable)
    }

    fun stop() {
        playing = false
        handler.removeCallbacks(advanceRunnable)
    }

    fun scheduleNext(delayMs: Long) {
        handler.removeCallbacks(advanceRunnable)
        if (!playing) return
        val delay = (delayMs / speed.toDouble()).toLong().coerceAtLeast(MIN_DELAY_MS)
        handler.postDelayed(advanceRunnable, delay)
    }

    fun clear() {
        stop()
        speed = 1f
    }

    fun delayUntilNext(current: ReplayFrame?, next: ReplayFrame?): Long {
        if (current == null || next == null) return DEFAULT_STEP_MS
        val deltaMs = ((next.timestampNs - current.timestampNs) / 1_000_000L).coerceAtLeast(MIN_DELAY_MS)
        return deltaMs.coerceAtMost(MAX_STEP_MS)
    }

    companion object {
        private const val MIN_SPEED = 0.25f
        private const val MAX_SPEED = 8f
        private const val MIN_DELAY_MS = 16L
        private const val DEFAULT_STEP_MS = 100L
        private const val MAX_STEP_MS = 2_000L
    }
}
