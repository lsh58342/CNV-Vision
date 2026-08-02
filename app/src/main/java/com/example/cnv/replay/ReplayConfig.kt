package com.example.cnv.replay

/**
 * Replay Engine configuration (STEP 16).
 */
data class ReplayConfig(
    val defaultIndex: Int = 0,
) {
    companion object {
        val DEFAULT = ReplayConfig()
    }
}
