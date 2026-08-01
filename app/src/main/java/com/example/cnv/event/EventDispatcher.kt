package com.example.cnv.event

/**
 * Thin dispatcher so producers do not depend on EventBus static usage patterns.
 */
class EventDispatcher {

    fun dispatch(event: BaseEvent) {
        EventBus.publish(event)
    }

    fun <T : BaseEvent> subscribe(eventType: Class<T>, listener: (T) -> Unit) {
        EventBus.subscribe(eventType, listener)
    }

    fun <T : BaseEvent> unsubscribe(eventType: Class<T>, listener: (T) -> Unit) {
        EventBus.unsubscribe(eventType, listener)
    }
}
