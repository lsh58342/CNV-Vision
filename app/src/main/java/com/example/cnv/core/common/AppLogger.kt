package com.example.cnv.core.common

import android.util.Log

/**
 * Thin logger facade (STEP 20: prefer ProductionLog for Error/Warning/Performance).
 */
object AppLogger {
    fun d(tag: String, message: String) {
        if (com.example.cnv.core.config.DebugConfig.DEFAULT.showOpenCvOverlay) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
