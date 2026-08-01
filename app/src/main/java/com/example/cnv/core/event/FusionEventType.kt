package com.example.cnv.core.event

/**
 * Fusion outcome type on the event bus.
 * Position is never included (STEP 10 Map Matching).
 */
enum class FusionEventType {
    /** Continuous distance without a matched shock (drives Map Matching). */
    DISTANCE_ONLY,

    /** Distance matched with shock inside the time window. */
    DISTANCE_AND_SHOCK,

    /** Shock without a matched distance sample. */
    SHOCK_ONLY,
}
