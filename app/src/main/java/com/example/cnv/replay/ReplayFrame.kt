package com.example.cnv.replay

/**
 * Cached Replay frame DTO exposed via [ReplayEngineApi.events] / [ReplayEngineApi.currentEvent].
 * Built once inside Engine; consumers must not rebuild from Room.
 */
data class ReplayFrame(
    val index: Int,
    val eventId: Long,
    val sessionId: String,
    val timestampNs: Long,
    val elapsedMs: Long,
    val distanceMm: Float,
    val routePositionMm: Float,
    val segmentId: String?,
    val progress: Float?,
    val drawingX: Double?,
    val drawingY: Double?,
    val hasShock: Boolean,
    val shockStrength: Float,
    val trackingConfidence: Float,
    val zoneId: String?,
    val zoneName: String?,
    val eventType: String,
)
