package com.example.cnv.replay.analysis

/**
 * Replay Analysis filter state — applied to Engine-cached frames only.
 */
data class ReplayFilter(
    val shocksOnly: Boolean = false,
    val lowConfidenceOnly: Boolean = false,
    val zoneId: String? = null,
    val timeFromNs: Long? = null,
    val timeToNs: Long? = null,
) {
    fun isActive(): Boolean =
        shocksOnly ||
            lowConfidenceOnly ||
            !zoneId.isNullOrBlank() ||
            timeFromNs != null ||
            timeToNs != null

    companion object {
        val NONE = ReplayFilter()
    }
}
