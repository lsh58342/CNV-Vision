package com.example.cnv.core.event

/**
 * Feature-facing dispatcher that delegates to an injectable [EventBus].
 */
class EventDispatcher(
    private val eventBus: EventBus,
) : EventPublisher, EventSubscriber {

    fun dispatch(event: BaseEvent) {
        eventBus.publish(event)
    }

    override fun publish(event: BaseEvent) {
        eventBus.publish(event)
    }

    override fun <T : BaseEvent> subscribe(eventType: Class<T>, listener: (T) -> Unit) {
        eventBus.subscribe(eventType, listener)
    }

    override fun <T : BaseEvent> unsubscribe(eventType: Class<T>, listener: (T) -> Unit) {
        eventBus.unsubscribe(eventType, listener)
    }
}
