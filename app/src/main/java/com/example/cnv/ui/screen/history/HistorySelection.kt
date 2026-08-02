package com.example.cnv.ui.screen.history

/**
 * Cross-screen selection for History → Session Detail / HeatMap / Replay (STEP 15-3).
 * Does not own algorithms — session id pointer only.
 */
object HistorySelection {
    @Volatile
    var selectedSessionId: String? = null
        private set

    @Volatile
    var selectedDrawingId: String? = null
        private set

    fun select(drawingId: String, sessionId: String) {
        selectedDrawingId = drawingId
        selectedSessionId = sessionId
    }

    fun clear() {
        selectedDrawingId = null
        selectedSessionId = null
    }
}
