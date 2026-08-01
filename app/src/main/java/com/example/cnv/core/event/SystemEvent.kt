package com.example.cnv.core.event

/**
 * Cross-cutting system signals (lifecycle / readiness). Fusion may subscribe later.
 */
data class SystemEvent(
    override val timestampNs: Long,
    val type: Type,
    val message: String = "",
) : BaseEvent {

    enum class Type {
        APP_READY,
        FEATURE_STARTED,
        FEATURE_STOPPED,
        WARNING,
        ERROR,
    }
}
