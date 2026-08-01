package com.example.cnv.factory.model

/**
 * Logical inspection segment on a Route. Zone is the top-level inspection unit.
 * Bounds are stored as Route anchors (never CAD world coords).
 */
data class Zone(
    val id: String,
    val floorId: String,
    val routeId: String,
    val name: String,
    val colorLabel: String,
    val colorArgb: Int,
    val start: RouteAnchor,
    val end: RouteAnchor,
    val calibrationVersion: Int? = null,
    val dwgRegistered: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
