package com.example.cnv.factory.repository

import com.example.cnv.core.event.BaseEvent
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.inspection.PersistedInspectionSession
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.speed.SpeedValidationSummary

/**
 * Drawing-scoped inspection history index + Room persistence facade.
 * STEP 15-4: Room-touching APIs must be invoked from background (or via *Async helpers).
 */
class ZoneInspectionRepository(
    private val inspectionRepository: InspectionRepository = InspectionRepository(),
) {

    private val lock = Any()
    private val drawingToSessionIds = LinkedHashMap<String, ArrayDeque<String>>()

    fun save(drawingId: String, result: InspectionResult) {
        val exists = inspectionRepository.all().any { it.sessionId == result.sessionId }
        if (!exists) {
            inspectionRepository.save(result)
        }
        index(drawingId, result.sessionId)
    }

    /** Background-thread only. */
    fun finishSession(
        drawingId: String,
        result: InspectionResult,
        events: List<BaseEvent>,
        appVersion: String,
        inspectionVersion: String = "1",
        speedValidation: SpeedValidationSummary = SpeedValidationSummary.EMPTY,
        conveyorProfile: ConveyorProfileSnapshot? = null,
    ): InspectionSessionSummary {
        val summary = inspectionRepository.finishSession(
            drawingId = drawingId,
            result = result,
            events = events,
            appVersion = appVersion,
            inspectionVersion = inspectionVersion,
            speedValidation = speedValidation,
            conveyorProfile = conveyorProfile,
        )
        index(drawingId, result.sessionId)
        return summary
    }

    fun finishSessionAsync(
        drawingId: String,
        result: InspectionResult,
        events: List<BaseEvent>,
        appVersion: String,
        inspectionVersion: String = "1",
        speedValidation: SpeedValidationSummary = SpeedValidationSummary.EMPTY,
        conveyorProfile: ConveyorProfileSnapshot? = null,
        onDone: ((InspectionSessionSummary) -> Unit)? = null,
    ) {
        InspectionDbGate.submit(
            block = {
                finishSession(
                    drawingId = drawingId,
                    result = result,
                    events = events,
                    appVersion = appVersion,
                    inspectionVersion = inspectionVersion,
                    speedValidation = speedValidation,
                    conveyorProfile = conveyorProfile,
                )
            },
            onMain = { summary -> onDone?.invoke(summary) },
        )
    }

    /** Background-thread only. */
    fun createSession(
        drawingId: String,
        sessionId: String,
        startTimeMs: Long,
        appVersion: String,
        routeVersion: String = "",
        calibrationVersion: Int = 0,
        conveyorProfile: ConveyorProfileSnapshot = ConveyorProfileSnapshot.empty(),
        ruleCatalogVersion: Int = 0,
    ) {
        inspectionRepository.createSession(
            sessionId = sessionId,
            drawingId = drawingId,
            startTimeMs = startTimeMs,
            appVersion = appVersion,
            routeVersion = routeVersion,
            calibrationVersion = calibrationVersion,
            conveyorProfile = conveyorProfile,
            ruleCatalogVersion = ruleCatalogVersion,
        )
        index(drawingId, sessionId)
    }

    fun createSessionAsync(
        drawingId: String,
        sessionId: String,
        startTimeMs: Long,
        appVersion: String,
        routeVersion: String = "",
        calibrationVersion: Int = 0,
        conveyorProfile: ConveyorProfileSnapshot = ConveyorProfileSnapshot.empty(),
        ruleCatalogVersion: Int = 0,
    ) {
        InspectionDbGate.execute {
            createSession(
                drawingId = drawingId,
                sessionId = sessionId,
                startTimeMs = startTimeMs,
                appVersion = appVersion,
                routeVersion = routeVersion,
                calibrationVersion = calibrationVersion,
                conveyorProfile = conveyorProfile,
                ruleCatalogVersion = ruleCatalogVersion,
            )
        }
    }

    /** Background-thread only. */
    fun loadSession(sessionId: String): PersistedInspectionSession? =
        inspectionRepository.loadSession(sessionId)

    fun loadSessionAsync(sessionId: String, onResult: (PersistedInspectionSession?) -> Unit) {
        InspectionDbGate.submit(
            block = { loadSession(sessionId) },
            onMain = onResult,
        )
    }

    /** Background-thread only. */
    fun deleteSession(sessionId: String) {
        inspectionRepository.deleteSession(sessionId)
        synchronized(lock) {
            drawingToSessionIds.values.forEach { q -> q.remove(sessionId) }
        }
    }

    fun deleteSessionAsync(sessionId: String, onDone: (() -> Unit)? = null) {
        InspectionDbGate.submit(
            block = { deleteSession(sessionId) },
            onMain = { onDone?.invoke() },
        )
    }

    /** Background-thread only. */
    fun loadHistorySummaries(drawingId: String): List<InspectionSessionSummary> =
        inspectionRepository.loadHistory(drawingId)

    fun loadHistorySummariesAsync(
        drawingId: String,
        onResult: (List<InspectionSessionSummary>) -> Unit,
    ) {
        InspectionDbGate.submit(
            block = { loadHistorySummaries(drawingId) },
            onMain = onResult,
        )
    }

    fun index(drawingId: String, sessionId: String) {
        synchronized(lock) {
            val q = drawingToSessionIds.getOrPut(drawingId) { ArrayDeque() }
            if (!q.contains(sessionId)) {
                q.addLast(sessionId)
            }
            while (q.size > 50) q.removeFirst()
        }
    }

    /** Background-thread only when Room path is used. */
    fun historyForDrawing(drawingId: String): List<InspectionResult> {
        val fromRoom = inspectionRepository.historyAsResults(drawingId)
        if (fromRoom.isNotEmpty()) return fromRoom
        val ids = synchronized(lock) { drawingToSessionIds[drawingId]?.toList().orEmpty() }
        if (ids.isEmpty()) return emptyList()
        val all = inspectionRepository.all().associateBy { it.sessionId }
        return ids.mapNotNull { all[it] }
    }

    fun historyForDrawingAsync(drawingId: String, onResult: (List<InspectionResult>) -> Unit) {
        InspectionDbGate.submit(
            block = { historyForDrawing(drawingId) },
            onMain = onResult,
        )
    }

    fun latestForDrawingAsync(drawingId: String, onResult: (InspectionResult?) -> Unit) {
        historyForDrawingAsync(drawingId) { onResult(it.lastOrNull()) }
    }

    /** Background-thread only. Prefer [latestForDrawingAsync] from UI. */
    fun latestForDrawing(drawingId: String): InspectionResult? =
        historyForDrawing(drawingId).lastOrNull()

    fun historyForCurrentDrawing(context: CurrentContext = CurrentContext.get()): List<InspectionResult> {
        val drawingId = context.drawingId ?: return emptyList()
        return historyForDrawing(drawingId)
    }

    fun historyForZone(zoneId: String): List<InspectionResult> = historyForDrawing(zoneId)

    fun historyForCurrentZone(context: CurrentContext = CurrentContext.get()): List<InspectionResult> =
        historyForCurrentDrawing(context)

    /** Background-thread only. */
    fun removeForDrawing(drawingId: String) {
        inspectionRepository.deleteForDrawing(drawingId)
        synchronized(lock) {
            drawingToSessionIds.remove(drawingId)
        }
    }

    fun removeForDrawingAsync(drawingId: String, onDone: (() -> Unit)? = null) {
        InspectionDbGate.submit(
            block = { removeForDrawing(drawingId) },
            onMain = { onDone?.invoke() },
        )
    }

    fun underlying(): InspectionRepository = inspectionRepository
}
