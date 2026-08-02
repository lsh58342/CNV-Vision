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
 * CAD multi-select: toggle Route segment/polyline ids → name/color → save
 * Legacy CAD: pick start → pick end → name/color → save
 * Drive: record → mark start/end from Route Position → name/color → save
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
            mode = ZoneEditorMode.CAD_MULTI_SELECT,
        )
        return true
    }

    /** Legacy two-pick flow (start → end). */
    fun beginCadStartEndCreation(): Boolean {
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
        val route = catalog.routes.currentRoute()
        val ids = if (route != null) {
            ZonePolylineResolver.resolvedIds(zone, route)
        } else {
            zone.polylineIds
        }
        draft = ZoneEditorDraft(
            zoneId = zone.id,
            drawingId = zone.drawingId,
            routeId = zone.routeId,
            name = zone.name,
            colorLabel = zone.colorLabel,
            colorArgb = zone.colorArgb,
            start = zone.start,
            end = zone.end,
            polylineIds = ids,
            mode = ZoneEditorMode.CAD_MULTI_SELECT,
        )
        return true
    }

    fun togglePolyline(polylineId: String): Boolean {
        if (draft.mode != ZoneEditorMode.CAD_MULTI_SELECT) return false
        val id = polylineId.trim()
        if (id.isEmpty() || id == "—") return false
        val next = draft.polylineIds.toMutableList()
        if (next.any { it.equals(id, ignoreCase = true) }) {
            next.removeAll { it.equals(id, ignoreCase = true) }
        } else {
            next.add(id)
        }
        draft = draft.copy(polylineIds = next, start = anchorsFromPolylines(next).first, end = anchorsFromPolylines(next).second)
        return true
    }

    fun setPolylines(ids: List<String>): Boolean {
        if (draft.mode != ZoneEditorMode.CAD_MULTI_SELECT &&
            draft.mode != ZoneEditorMode.NAME_COLOR
        ) {
            return false
        }
        val cleaned = ids.map { it.trim() }.filter { it.isNotEmpty() && it != "—" }.distinct()
        val anchors = anchorsFromPolylines(cleaned)
        draft = draft.copy(polylineIds = cleaned, start = anchors.first, end = anchors.second)
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
        val ids = listOfNotNull(anchor.segmentId ?: draft.start.segmentId)
        draft = draft.copy(
            end = anchor,
            polylineIds = ids,
            mode = ZoneEditorMode.NAME_COLOR,
        )
        return true
    }

    fun setName(name: String) {
        draft = draft.copy(name = name.trim())
    }

    fun setColor(label: String, argb: Int) {
        draft = draft.copy(colorLabel = label, colorArgb = argb)
    }

    fun prepareNameColor(): Boolean {
        val d = draft
        if (d.mode != ZoneEditorMode.CAD_MULTI_SELECT) return false
        if (d.polylineIds.isEmpty()) return false
        draft = d.copy(mode = ZoneEditorMode.NAME_COLOR)
        return true
    }

    fun save(): Zone? {
        if (!isAccessible()) return null
        val d = draft
        if (!d.canSave()) return null
        val drawing = catalog.drawings.get(d.drawingId) ?: return null
        if (drawing.routeLocked) return null
        val now = System.currentTimeMillis()
        val anchors = if (d.polylineIds.isNotEmpty()) {
            anchorsFromPolylines(d.polylineIds)
        } else {
            d.start to d.end
        }
        val zone = Zone(
            id = d.zoneId ?: UUID.randomUUID().toString(),
            drawingId = d.drawingId,
            routeId = d.routeId,
            name = d.name,
            colorLabel = d.colorLabel,
            colorArgb = d.colorArgb,
            start = anchors.first,
            end = anchors.second,
            polylineIds = d.polylineIds.ifEmpty {
                listOfNotNull(anchors.first.segmentId, anchors.second.segmentId).distinct()
            },
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

    private fun anchorsFromPolylines(ids: List<String>): Pair<RouteAnchor, RouteAnchor> {
        if (ids.isEmpty()) return RouteAnchor() to RouteAnchor()
        val first = ids.first()
        val last = ids.last()
        return RouteAnchor(segmentId = first, progress = 0f) to
            RouteAnchor(segmentId = last, progress = 1f)
    }
}
