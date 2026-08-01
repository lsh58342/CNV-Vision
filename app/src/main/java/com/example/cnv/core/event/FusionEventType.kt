package com.example.cnv.core.event

/**
 * Fusion outcome type on the event bus.
 * Position is never included (STEP 10 Map Matching).
 */
enum class FusionEventType {
    FUSED,
    DISTANCE_ONLY,
    SHOCK_ONLY,
}
