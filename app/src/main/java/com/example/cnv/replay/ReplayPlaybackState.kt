package com.example.cnv.replay

/**
 * Public Replay playback state (STEP 16-2).
 * Mirrors the Engine State Machine without exposing internals.
 */
enum class ReplayPlaybackState {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    COMPLETED,
    STOPPED,
    ERROR,
}
