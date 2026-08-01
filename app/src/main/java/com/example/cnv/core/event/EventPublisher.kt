package com.example.cnv.core.event

/**
 * Publishes immutable [BaseEvent] instances to the bus.
 */
fun interface EventPublisher {
    fun publish(event: BaseEvent)
}
