package com.example.cnv.core.event

/**
 * In-memory publish/subscribe bus.
 * Constructed via DI / [CoreEventModule] — not a Kotlin `object` singleton.
 */
class EventBus : EventPublisher, EventSubscriber {

    private val lock = Any()
    private val subscribers =
        mutableMapOf<Class<out BaseEvent>, MutableList<(BaseEvent) -> Unit>>()

    override fun <T : BaseEvent> subscribe(eventType: Class<T>, listener: (T) -> Unit) {
        synchronized(lock) {
            val list = subscribers.getOrPut(eventType) { mutableListOf() }
            @Suppress("UNCHECKED_CAST")
            list.add(listener as (BaseEvent) -> Unit)
        }
    }

    override fun <T : BaseEvent> unsubscribe(eventType: Class<T>, listener: (T) -> Unit) {
        synchronized(lock) {
            val list = subscribers[eventType] ?: return
            @Suppress("UNCHECKED_CAST")
            list.remove(listener as (BaseEvent) -> Unit)
            if (list.isEmpty()) {
                subscribers.remove(eventType)
            }
        }
    }

    override fun publish(event: BaseEvent) {
        val listeners = synchronized(lock) {
            subscribers[event.javaClass]?.toList().orEmpty()
        }
        for (listener in listeners) {
            listener(event)
        }
    }

    fun clear() {
        synchronized(lock) {
            subscribers.clear()
        }
    }
}
