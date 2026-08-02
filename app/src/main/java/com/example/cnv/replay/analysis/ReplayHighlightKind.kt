package com.example.cnv.replay.analysis

/**
 * Overlay highlight classification for the current / listed Replay frames.
 * [RULE] is driven by cached Rule Result (STEP 18) — Replay does not re-evaluate rules.
 */
enum class ReplayHighlightKind {
    /** Current position marker — green. */
    CURRENT,
    /** Shock event — red. */
    SHOCK,
    /** Tracking confidence below threshold — orange. */
    LOW_CONFIDENCE,
    /** Rule Result highlight (Critical/High) — magenta. */
    RULE,
    NONE,
}
