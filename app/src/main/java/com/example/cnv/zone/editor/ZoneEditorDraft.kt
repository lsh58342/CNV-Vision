package com.example.cnv.zone.editor

import com.example.cnv.factory.model.RouteAnchor

/**
 * Mutable draft while creating/editing a Zone in Commissioning.
 * Does not touch CAD / Inspection algorithms.
 */
data class ZoneEditorDraft(
    val zoneId: String? = null,
    val floorId: String = "",
    val routeId: String = "",
    val name: String = "",
    val colorLabel: String = "Orange",
    val colorArgb: Int = 0xFFFF9800.toInt(),
    val start: RouteAnchor = RouteAnchor(),
    val end: RouteAnchor = RouteAnchor(),
    val mode: ZoneEditorMode = ZoneEditorMode.IDLE,
) {
    fun canSave(): Boolean =
        name.isNotBlank() &&
            floorId.isNotBlank() &&
            routeId.isNotBlank() &&
            start.isDefined() &&
            end.isDefined()
}
