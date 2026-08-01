package com.example.cnv.core.event

/**
 * Immutable marker for all domain events.
 */
interface BaseEvent {
    val timestampNs: Long
}
