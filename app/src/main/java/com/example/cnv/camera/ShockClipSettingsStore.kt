package com.example.cnv.camera

import android.content.Context
import com.example.cnv.util.AppFileGuard
import java.io.File

/** Pre/post buffer seconds for shock-triggered back-camera clips. */
object ShockClipSettingsStore {

    private const val PREFS = "cnv_shock_clip_settings"
    private const val KEY_PRE_SEC = "pre_sec"
    private const val KEY_POST_SEC = "post_sec"
    private const val KEY_ENABLED = "enabled"

    const val DEFAULT_PRE_SEC = 2f
    const val DEFAULT_POST_SEC = 3f
    const val SEGMENT_SEC = 1f

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun preSec(context: Context): Float =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_PRE_SEC, DEFAULT_PRE_SEC)
            .coerceIn(0.5f, 10f)

    fun postSec(context: Context): Float =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_POST_SEC, DEFAULT_POST_SEC)
            .coerceIn(0.5f, 15f)

    fun save(context: Context, preSec: Float, postSec: Float, enabled: Boolean = true): Boolean {
        if (postSec <= 0f || preSec < 0f) return false
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_PRE_SEC, preSec.coerceIn(0.5f, 10f))
            .putFloat(KEY_POST_SEC, postSec.coerceIn(0.5f, 15f))
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        return true
    }

    fun resetDefaults(context: Context) {
        save(context, DEFAULT_PRE_SEC, DEFAULT_POST_SEC, enabled = true)
    }
}

/** Deletes persisted shock clip folders without pulling Application into repositories. */
object ShockClipStorage {
    private var clipRoot: File? = null

    fun bind(context: Context) {
        clipRoot = File(context.applicationContext.filesDir, "shock_clips").also { it.mkdirs() }
    }

    fun deleteSession(sessionId: String) {
        val root = clipRoot ?: return
        AppFileGuard.childDir(root, sessionId)?.deleteRecursively()
    }

    fun resolveClipFile(path: String): File? {
        val root = clipRoot ?: return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return AppFileGuard.fileUnderRoot(root, file)
    }
}
