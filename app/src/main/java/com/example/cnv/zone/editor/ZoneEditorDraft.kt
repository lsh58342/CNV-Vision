package com.example.cnv.zone.editor

import com.example.cnv.factory.model.RouteAnchor

/**
 * Mutable draft while creating/editing a Zone in Commissioning.
 * Does not touch CAD / Inspection algorithms.
 */
data class ZoneEditorDraft(
    val zoneId: String? = null,
    val drawingId: String = "",
    val routeId: String = "",
    val name: String = "",
    val colorLabel: String = "Orange",
    val colorArgb: Int = 0xFFFF9800.toInt(),
    val start: RouteAnchor = RouteAnchor(),
    val end: RouteAnchor = RouteAnchor(),
    /** Multi-select Route segment / polyline ids. */
    val polylineIds: List<String> = emptyList(),
    val mode: ZoneEditorMode = ZoneEditorMode.IDLE,
) {
    fun canSave(): Boolean {
        if (name.isBlank() || drawingId.isBlank() || routeId.isBlank()) return false
        if (polylineIds.isNotEmpty()) return true
        return start.isDefined() && end.isDefined()
    }

    fun selectedCount(): Int = polylineIds.size
}
