package com.example.cnv.event

/**
 * Thread-safe publish/subscribe bus. Camera and IMU never call each other —
 * they only publish/subscribe [BaseEvent] subtypes.
 */
object EventBus {

    private val lock = Any()
    private val subscribers =
        mutableMapOf<Class<out BaseEvent>, MutableList<(BaseEvent) -> Unit>>()

    fun <T : BaseEvent> subscribe(eventType: Class<T>, listener: (T) -> Unit) {
        synchronized(lock) {
            val list = subscribers.getOrPut(eventType) { mutableListOf() }
            @Suppress("UNCHECKED_CAST")
            list.add(listener as (BaseEvent) -> Unit)
        }
    }

    fun <T : BaseEvent> unsubscribe(eventType: Class<T>, listener: (T) -> Unit) {
        synchronized(lock) {
            val list = subscribers[eventType] ?: return
            @Suppress("UNCHECKED_CAST")
            list.remove(listener as (BaseEvent) -> Unit)
            if (list.isEmpty()) {
                subscribers.remove(eventType)
            }
        }
    }

    fun publish(event: BaseEvent) {
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
