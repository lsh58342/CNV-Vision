package com.example.cnv.core.event

/**
 * DI-ready process-wide event wiring. Replaceable later with a DI framework.
 * Not an EventBus singleton — holds a single [EventBus] instance for the app process.
 */
object CoreEventModule {

    @Volatile
    private var bus: EventBus? = null

    fun eventBus(): EventBus {
        return bus ?: synchronized(this) {
            bus ?: EventBus().also { bus = it }
        }
    }

    fun eventDispatcher(): EventDispatcher = EventDispatcher(eventBus())

    /** Test-only: reset shared bus. */
    fun resetForTests() {
        synchronized(this) {
            bus?.clear()
            bus = null
        }
    }
}
