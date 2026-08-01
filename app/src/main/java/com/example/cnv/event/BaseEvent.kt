package com.example.cnv.event

/**
 * Marker for all bus events. Modules communicate only through events.
 */
interface BaseEvent {
    val timestampNs: Long
}
