package com.example.cnv.ui.navigation

import android.app.Activity
import android.content.Intent
import com.example.cnv.MainActivity
import com.example.cnv.factory.context.AppMode
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.context.canAccessCommissioning
import com.example.cnv.ui.commissioning.CommissioningHomeActivity
import com.example.cnv.ui.commissioning.ZoneEditorActivity
import com.example.cnv.ui.operation.BuildingSelectActivity
import com.example.cnv.ui.operation.FactorySelectActivity
import com.example.cnv.ui.operation.FloorSelectActivity
import com.example.cnv.ui.operation.ZoneDashboardActivity
import com.example.cnv.ui.operation.ZoneListActivity
import com.example.cnv.ui.settings.SettingsActivity

/**
 * Central Operation / Commissioning navigation.
 * Does not embed inspection algorithms.
 */
object AppNavigator {

    const val EXTRA_ZONE_ID = "extra_zone_id"

    fun openFactorySelect(from: Activity) {
        from.startActivity(Intent(from, FactorySelectActivity::class.java))
    }

    fun openBuildingSelect(from: Activity) {
        from.startActivity(Intent(from, BuildingSelectActivity::class.java))
    }

    fun openFloorSelect(from: Activity) {
        from.startActivity(Intent(from, FloorSelectActivity::class.java))
    }

    fun openZoneList(from: Activity) {
        from.startActivity(Intent(from, ZoneListActivity::class.java))
    }

    fun openZoneDashboard(from: Activity, zoneId: String? = null) {
        zoneId?.let { CurrentContext.get().selectZone(it) }
        from.startActivity(Intent(from, ZoneDashboardActivity::class.java))
    }

    fun openInspection(from: Activity, zoneId: String? = CurrentContext.get().zoneId) {
        val intent = Intent(from, MainActivity::class.java)
        zoneId?.let { intent.putExtra(EXTRA_ZONE_ID, it) }
        from.startActivity(intent)
    }

    fun openSettings(from: Activity) {
        from.startActivity(Intent(from, SettingsActivity::class.java))
    }

    fun openCommissioningHome(from: Activity): Boolean {
        val ctx = CurrentContext.get()
        if (!ctx.accessRole.canAccessCommissioning()) return false
        ctx.setAppMode(AppMode.COMMISSIONING)
        from.startActivity(Intent(from, CommissioningHomeActivity::class.java))
        return true
    }

    fun openZoneEditor(from: Activity): Boolean {
        val ctx = CurrentContext.get()
        if (ctx.appMode != AppMode.COMMISSIONING) return false
        if (!ctx.accessRole.canAccessCommissioning()) return false
        from.startActivity(Intent(from, ZoneEditorActivity::class.java))
        return true
    }

    fun leaveCommissioning(from: Activity) {
        CurrentContext.get().setAppMode(AppMode.OPERATION)
        openFactorySelect(from)
        from.finish()
    }
}
