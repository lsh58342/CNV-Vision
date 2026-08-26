package com.example.cnv.util

import java.io.File

/** Validates session-scoped paths under filesDir to block traversal via tampered IDs/paths. */
object AppFileGuard {

    private val SESSION_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun isSafeSessionId(sessionId: String): Boolean = SESSION_ID.matches(sessionId)

    fun childDir(root: File, sessionId: String): File? {
        if (!isSafeSessionId(sessionId)) return null
        root.mkdirs()
        return File(root, sessionId)
    }

    /** Returns the file only when its canonical path stays under [root]. */
    fun fileUnderRoot(root: File, file: File): File? {
        return runCatching {
            root.mkdirs()
            val canonicalRoot = root.canonicalFile
            val canonical = file.canonicalFile
            val prefix = canonicalRoot.path + File.separator
            if (canonical.path == canonicalRoot.path || canonical.path.startsWith(prefix)) {
                canonical
            } else {
                null
            }
        }.getOrNull()
    }
}
