package com.example.cnv.inspection

/**
 * Active inspection session with frozen context + append-only recorder.
 */
class InspectionSession(
    val sessionId: String,
    val freeze: InspectionFreezeSnapshot,
    val routeSnapshot: RouteSnapshot,
    val startTimeMs: Long,
    private val recorder: InspectionRecorder = InspectionRecorder(),
) {

    @Volatile
    var state: InspectionState = InspectionState.RUNNING
        private set

    fun recorder(): InspectionRecorder = recorder

    fun elapsedMs(nowMs: Long = System.currentTimeMillis()): Long =
        (nowMs - startTimeMs).coerceAtLeast(0L)

    fun markStopped() {
        state = InspectionState.STOPPED
    }

    fun buildResult(endTimeMs: Long): InspectionResult {
        val statistics = recorder.computeStatistics(freeze, startTimeMs, endTimeMs)
        return InspectionResult(
            sessionId = sessionId,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            durationMs = (endTimeMs - startTimeMs).coerceAtLeast(0L),
            routeVersion = freeze.routeVersion,
            calibrationVersion = freeze.calibrationVersion,
            statistics = statistics,
            routeQualityScore = freeze.routeQualityScore,
        )
    }
}
