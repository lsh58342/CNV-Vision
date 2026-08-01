package com.example.cnv.core.common

/**
 * Injectable clock abstraction for events and sensors.
 */
fun interface TimeProvider {
    fun nanoTime(): Long
}

class SystemTimeProvider : TimeProvider {
    override fun nanoTime(): Long = System.nanoTime()
}
