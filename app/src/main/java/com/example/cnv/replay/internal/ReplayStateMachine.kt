package com.example.cnv.replay.internal

import com.example.cnv.replay.ReplayPlaybackState

/**
 * Replay State Machine — Idle / Loading / Ready / Playing / Paused / Completed / Stopped / Error.
 */
internal class ReplayStateMachine {

    @Volatile
    private var state: ReplayPlaybackState = ReplayPlaybackState.IDLE

    fun state(): ReplayPlaybackState = state

    fun toLoading(): Boolean = transition(from = setOf(ReplayPlaybackState.IDLE, ReplayPlaybackState.STOPPED, ReplayPlaybackState.ERROR, ReplayPlaybackState.READY, ReplayPlaybackState.COMPLETED), to = ReplayPlaybackState.LOADING)

    fun toReady(): Boolean = transition(from = setOf(ReplayPlaybackState.LOADING), to = ReplayPlaybackState.READY)

    fun toPlaying(): Boolean = transition(
        from = setOf(ReplayPlaybackState.READY, ReplayPlaybackState.PAUSED, ReplayPlaybackState.COMPLETED, ReplayPlaybackState.STOPPED),
        to = ReplayPlaybackState.PLAYING,
    )

    fun toPaused(): Boolean = transition(from = setOf(ReplayPlaybackState.PLAYING), to = ReplayPlaybackState.PAUSED)

    fun toCompleted(): Boolean = transition(from = setOf(ReplayPlaybackState.PLAYING, ReplayPlaybackState.READY, ReplayPlaybackState.PAUSED), to = ReplayPlaybackState.COMPLETED)

    fun toStopped(): Boolean = run {
        state = ReplayPlaybackState.STOPPED
        true
    }

    fun toError(): Boolean = run {
        state = ReplayPlaybackState.ERROR
        true
    }

    fun toIdle(): Boolean = run {
        state = ReplayPlaybackState.IDLE
        true
    }

    private fun transition(from: Set<ReplayPlaybackState>, to: ReplayPlaybackState): Boolean {
        if (state !in from) return false
        state = to
        return true
    }
}
