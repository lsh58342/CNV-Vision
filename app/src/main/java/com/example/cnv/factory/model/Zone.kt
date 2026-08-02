package com.example.cnv.factory.model

/**
 * Logical inspection segment on a Drawing Route.
 * Bounds are stored as Route anchors (never CAD world coords).
 *
 * [polylineIds] holds the Route segment / polyline collection for this Zone.
 * Empty list keeps backward compatibility: consumers derive segments from [start]/[end].
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
    val polylineIds: List<String> = emptyList(),
    val calibrationVersion: Int? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
