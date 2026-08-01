package com.example.cnv.heatmap

/**
 * Session is the top-level HeatMap scope. Always applied before Timeline/Filter facets.
 */
class HeatMapSessionFilter {

    @Volatile
    private var activeSessionId: String? = null

    fun bind(sessionId: String?) {
        activeSessionId = sessionId?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        activeSessionId = null
    }

    fun currentSessionId(): String? = activeSessionId

    /** Keep only points belonging to the bound session (pass-through if unbound). */
    fun apply(points: List<HeatPoint>): List<HeatPoint> {
        val id = activeSessionId ?: return points
        return points.filter { it.sessionId == id }
    }

    fun summary(): String = "session=${activeSessionId?.take(8) ?: "—"}"
}
