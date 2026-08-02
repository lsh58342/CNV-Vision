package com.example.cnv.factory.context

import android.content.Context
import com.example.cnv.CnvApplication

/**
 * Persists [CurrentContext] selection across process restarts.
 */
object CurrentContextPrefs {
    private const val PREFS = "cnv_current_context"
    private const val KEY_FACTORY = "factoryId"
    private const val KEY_BUILDING = "buildingId"
    private const val KEY_FLOOR = "floorId"
    private const val KEY_DRAWING = "drawingId"
    private const val KEY_ROUTE = "routeId"
    private const val KEY_ZONE = "zoneId"

    fun save(context: CurrentContext = CurrentContext.get()) {
        val app = runCatching { CnvApplication.get() }.getOrNull() ?: return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FACTORY, context.factoryId)
            .putString(KEY_BUILDING, context.buildingId)
            .putString(KEY_FLOOR, context.floorId)
            .putString(KEY_DRAWING, context.drawingId)
            .putString(KEY_ROUTE, context.routeId)
            .putString(KEY_ZONE, context.zoneId)
            .apply()
    }

    fun restore(context: CurrentContext = CurrentContext.get()) {
        val app = runCatching { CnvApplication.get() }.getOrNull() ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val factoryId = prefs.getString(KEY_FACTORY, null) ?: return
        context.selectFactory(factoryId)
        prefs.getString(KEY_BUILDING, null)?.let { context.selectBuilding(it) }
        prefs.getString(KEY_FLOOR, null)?.let { context.selectFloor(it) }
        prefs.getString(KEY_DRAWING, null)?.let { context.selectDrawing(it) }
        prefs.getString(KEY_ROUTE, null)?.let { context.selectRoute(it) }
        prefs.getString(KEY_ZONE, null)?.let { context.selectZone(it) }
    }
}
