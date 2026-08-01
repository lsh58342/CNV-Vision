package com.example.cnv.ui.navigation

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.example.cnv.factory.context.AppMode
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.context.canAccessCommissioning
import com.example.cnv.ui.calibration.CalibrationActivity
import com.example.cnv.ui.commissioning.ZoneEditorActivity

/**
 * Side-flows that remain as Activities (Calibration / Zone Editor).
 * Primary app flow uses [NavHost] fragments.
 */
object AppNavigator {

    const val EXTRA_ZONE_ID = "extra_zone_id"

    fun openCalibration(from: FragmentActivity) {
        from.startActivity(Intent(from, CalibrationActivity::class.java))
    }

    fun openZoneEditor(from: FragmentActivity): Boolean {
        val ctx = CurrentContext.get()
        if (ctx.appMode != AppMode.COMMISSIONING) return false
        if (!ctx.accessRole.canAccessCommissioning()) return false
        from.startActivity(Intent(from, ZoneEditorActivity::class.java))
        return true
    }
}
