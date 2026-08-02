package com.example.cnv.replay.analysis

/**
 * Overlay highlight classification for the current / listed Replay frames.
 */
enum class ReplayHighlightKind {
    /** Current position marker — green. */
    CURRENT,
    /** Shock event — red. */
    SHOCK,
    /** Tracking confidence below threshold — orange. */
    LOW_CONFIDENCE,
    NONE,
}
