package com.example.cnv.core.event

/**
 * Subscribes to typed [BaseEvent] streams.
 */
interface EventSubscriber {
    fun <T : BaseEvent> subscribe(eventType: Class<T>, listener: (T) -> Unit)

    fun <T : BaseEvent> unsubscribe(eventType: Class<T>, listener: (T) -> Unit)
}
