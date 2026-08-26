package com.example.cnv.report

import android.content.Context

/** Toggle for post-inspection automatic report generation. */
object AutoReportSettingsStore {

    private const val PREFS = "cnv_auto_report_settings"
    private const val KEY_ENABLED = "enabled"

    @Volatile
    private var enabled: Boolean = true

    fun bind(context: Context) {
        enabled = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }
}
