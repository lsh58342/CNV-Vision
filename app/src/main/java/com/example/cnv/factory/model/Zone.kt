package com.example.cnv.factory.model

/**
 * Logical inspection segment on a Drawing Route.
 * Bounds are stored as Route anchors (never CAD world coords).
 */
data class Zone(
    val id: String,
    val drawingId: String,
    val routeId: String,
    val name: String,
    val colorLabel: String,
    val colorArgb: Int,
    val start: RouteAnchor,
    val end: RouteAnchor,
    val calibrationVersion: Int? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
