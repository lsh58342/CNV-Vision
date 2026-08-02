package com.example.cnv.production

import com.example.cnv.core.common.AppLogger
import com.example.cnv.core.config.DebugConfig

/**
 * Production logging — Error / Warning / Performance only (STEP 20).
 * Debug verbosity is gated; algorithms are unaffected.
 */
object ProductionLog {

    private const val PERF_TAG = "CNV.Perf"

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        AppLogger.e(tag, message, throwable)
    }

    fun warning(tag: String, message: String) {
        AppLogger.w(tag, message)
    }

    fun performance(message: String) {
        AppLogger.i(PERF_TAG, message)
    }

    /** Debug logs only when debug HUD flag is on — avoid spam in production. */
    fun debug(tag: String, message: String) {
        if (DebugConfig.DEFAULT.showImuHud) {
            AppLogger.d(tag, message)
        }
    }
}
