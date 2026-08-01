package com.example.cnv.event

/**
 * Published by CalibrationManager on session lifecycle changes.
 */
data class CalibrationEvent(
    override val timestampNs: Long,
    val type: Type,
    val mmPerPixel: Float,
    val totalObservedPixel: Float,
    val calibratedDistanceMm: Float,
) : BaseEvent {

    enum class Type {
        STARTED,
        FINISHED,
        CANCELLED,
        RESET,
    }
}
