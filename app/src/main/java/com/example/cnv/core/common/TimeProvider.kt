package com.example.cnv.core.common

/**
 * Injectable clock abstraction for events and sensors.
 * Implementations must use the unified [TimeBase] (elapsed-realtime ns).
 */
fun interface TimeProvider {
    fun nanoTime(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nanoTime(): Long = TimeBase.nowNs()
}
