package com.example.cnv.zone.editor

import com.example.cnv.factory.context.AppMode
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.FactoryCatalog
import java.util.UUID

/**
 * Commissioning-only Zone Editor controller (Drawing-scoped).
 *
 * Method 1 (CAD): pick start → pick end → name/color → save
 * Method 2 (Drive): record → mark start/end from Route Position → name/color → save
 */
class ZoneEditorController(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
    private val context: CurrentContext = CurrentContext.get(),
) {

    @Volatile
    private var draft: ZoneEditorDraft = ZoneEditorDraft()

    fun draft(): ZoneEditorDraft = draft

    fun isAccessible(): Boolean =
        context.appMode == AppMode.COMMISSIONING

    fun beginCadCreation(): Boolean {
        if (!isAccessible()) return false
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        val routeId = drawing.routeId ?: context.routeId ?: catalog.routes.currentRouteId() ?: return false
        draft = ZoneEditorDraft(
            drawingId = drawing.id,
            routeId = routeId,
            mode = ZoneEditorMode.CAD_PICK_START,
        )
        return true
    }

    fun beginDriveRecording(): Boolean {
        if (!isAccessible()) return false
        val drawing = catalog.drawings.current(context) ?: return false
        if (drawing.routeLocked) return false
        val routeId = drawing.routeId ?: context.routeId ?: catalog.routes.currentRouteId() ?: return false
        draft = ZoneEditorDraft(
            drawingId = drawing.id,
            routeId = routeId,
            mode = ZoneEditorMode.DRIVE_RECORDING,
        )
        return true
    }

    fun beginEdit(zoneId: String): Boolean {
        if (!isAccessible()) return false
        val zone = catalog.zones.get(zoneId) ?: return false
        val drawing = catalog.drawings.get(zone.drawingId) ?: return false
        if (drawing.routeLocked) return false
        draft = ZoneEditorDraft(
            zoneId = zone.id,
            drawingId = zone.drawingId,
            routeId = zone.routeId,
            name = zone.name,
            colorLabel = zone.colorLabel,
            colorArgb = zone.colorArgb,
            start = zone.start,
            end = zone.end,
            mode = ZoneEditorMode.NAME_COLOR,
        )
        return true
    }

    fun setCadStart(anchor: RouteAnchor): Boolean {
        if (draft.mode != ZoneEditorMode.CAD_PICK_START) return false
        draft = draft.copy(start = anchor, mode = ZoneEditorMode.CAD_PICK_END)
        return true
    }

    fun setCadEnd(anchor: RouteAnchor): Boolean {
        if (draft.mode != ZoneEditorMode.CAD_PICK_END) return false
        draft = draft.copy(end = anchor, mode = ZoneEditorMode.NAME_COLOR)
        return true
    }

    fun markDriveStart(anchor: RouteAnchor): Boolean {
        if (draft.mode != ZoneEditorMode.DRIVE_RECORDING &&
            draft.mode != ZoneEditorMode.DRIVE_MARK_START
        ) {
            return false
        }
        draft = draft.copy(start = anchor, mode = ZoneEditorMode.DRIVE_MARK_END)
        return true
    }

    fun markDriveEnd(anchor: RouteAnchor): Boolean {
        if (draft.mode != ZoneEditorMode.DRIVE_MARK_END) return false
        draft = draft.copy(end = anchor, mode = ZoneEditorMode.NAME_COLOR)
        return true
    }

    fun setName(name: String) {
        draft = draft.copy(name = name.trim())
    }

    fun setColor(label: String, argb: Int) {
        draft = draft.copy(colorLabel = label, colorArgb = argb)
    }

    fun save(): Zone? {
        if (!isAccessible()) return null
        val d = draft
        if (!d.canSave()) return null
        val drawing = catalog.drawings.get(d.drawingId) ?: return null
        if (drawing.routeLocked) return null
        val now = System.currentTimeMillis()
        val zone = Zone(
            id = d.zoneId ?: UUID.randomUUID().toString(),
            drawingId = d.drawingId,
            routeId = d.routeId,
            name = d.name,
            colorLabel = d.colorLabel,
            colorArgb = d.colorArgb,
            start = d.start,
            end = d.end,
            updatedAtMs = now,
            createdAtMs = catalog.zones.get(d.zoneId ?: "")?.createdAtMs ?: now,
        )
        catalog.zones.upsert(zone)
        draft = d.copy(zoneId = zone.id, mode = ZoneEditorMode.SAVED)
        return zone
    }

    fun delete(zoneId: String): Boolean {
        if (!isAccessible()) return false
        val zone = catalog.zones.get(zoneId) ?: return false
        val drawing = catalog.drawings.get(zone.drawingId) ?: return false
        if (drawing.routeLocked) return false
        return catalog.zones.delete(zoneId)
    }

    fun reset() {
        draft = ZoneEditorDraft()
    }
}
