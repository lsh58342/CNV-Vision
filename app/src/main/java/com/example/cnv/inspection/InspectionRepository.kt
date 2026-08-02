package com.example.cnv.inspection

import com.example.cnv.core.event.BaseEvent
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.factory.model.ConveyorDirection
import com.example.cnv.factory.model.ConveyorMotionProfile
import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.inspection.db.CnvInspectionDatabase
import com.example.cnv.inspection.db.InspectionEventEntity
import com.example.cnv.inspection.db.InspectionSessionEntity

/**
 * Inspection session store — in-memory cache + Room persistence (STEP 13).
 *
 * API: createSession / appendEvent / finishSession / loadSession / deleteSession / loadHistory
 * Keeps legacy save / latest / all for InspectionManager compatibility.
 */
class InspectionRepository(
    private val limit: Int = InspectionConfig.DEFAULT_CACHE_LIMIT,
) {

    private val lock = Any()
    private val results = ArrayDeque<InspectionResult>()

    /** Legacy in-memory save used by InspectionManager.stop. */
    fun save(result: InspectionResult) {
        synchronized(lock) {
            results.addLast(result)
            while (results.size > limit) {
                results.removeFirst()
            }
        }
    }

    fun latest(): InspectionResult? = synchronized(lock) { results.lastOrNull() }

    fun all(): List<InspectionResult> = synchronized(lock) { results.toList() }

    fun clear() {
        synchronized(lock) {
            results.clear()
        }
    }

    fun createSession(
        sessionId: String,
        drawingId: String,
        startTimeMs: Long,
        appVersion: String,
        inspectionVersion: String = "1",
        routeVersion: String = "",
        calibrationVersion: Int = 0,
        conveyorProfile: ConveyorProfileSnapshot = ConveyorProfileSnapshot.empty(),
    ) {
        val db = database() ?: return
        db.sessionDao().insertSession(
            InspectionSessionEntity(
                sessionId = sessionId,
                drawingId = drawingId,
                startTimeMs = startTimeMs,
                appVersion = appVersion,
                inspectionVersion = inspectionVersion,
                routeVersion = routeVersion,
                calibrationVersion = calibrationVersion,
                finished = false,
                profileNominalSpeedMPerMin = conveyorProfile.nominalSpeedMPerMin,
                profileDirection = conveyorProfile.direction.name,
                profileExpectedFps = conveyorProfile.expectedFps,
                profileMotionProfile = conveyorProfile.motionProfile.name,
            ),
        )
    }

    fun appendEvent(
        sessionId: String,
        drawingId: String,
        event: BaseEvent,
    ) {
        val db = database() ?: return
        db.eventDao().insertEvent(toEntity(sessionId, drawingId, event))
    }

    fun appendEvents(
        sessionId: String,
        drawingId: String,
        events: List<BaseEvent>,
    ) {
        val db = database() ?: return
        if (events.isEmpty()) return
        db.eventDao().insertEvents(events.map { toEntity(sessionId, drawingId, it) })
    }

    fun finishSession(
        drawingId: String,
        result: InspectionResult,
        events: List<BaseEvent>,
        appVersion: String,
        inspectionVersion: String = "1",
    ): InspectionSessionSummary {
        val durationSec = (result.durationMs / 1000f).coerceAtLeast(0.001f)
        val averageSpeed = result.statistics.totalDistanceMm / durationSec
        val coverage = result.routeQualityScore.coerceIn(0f, 1f)
        val summary = InspectionSessionSummary(
            sessionId = result.sessionId,
            drawingId = drawingId,
            startTimeMs = result.startTimeMs,
            endTimeMs = result.endTimeMs,
            durationMs = result.durationMs,
            totalDistanceMm = result.statistics.totalDistanceMm,
            shockCount = result.statistics.shockCount,
            averageSpeedMmPerSec = averageSpeed,
            maximumShock = result.statistics.maximumShockLevel,
            coverage = coverage,
            inspectionVersion = inspectionVersion,
            appVersion = appVersion,
        )
        val db = database()
        var profileSnap = ConveyorProfileSnapshot.empty()
        if (db != null) {
            val existing = db.sessionDao().getSession(result.sessionId)
            profileSnap = existing?.toProfileSnapshot() ?: ConveyorProfileSnapshot.empty()
            db.sessionDao().insertSession(
                InspectionSessionEntity(
                    sessionId = result.sessionId,
                    drawingId = drawingId,
                    startTimeMs = result.startTimeMs,
                    endTimeMs = result.endTimeMs,
                    durationMs = result.durationMs,
                    totalDistanceMm = summary.totalDistanceMm,
                    shockCount = summary.shockCount,
                    averageSpeedMmPerSec = summary.averageSpeedMmPerSec,
                    maximumShock = summary.maximumShock,
                    coverage = summary.coverage,
                    inspectionVersion = inspectionVersion,
                    appVersion = appVersion.ifBlank { existing?.appVersion.orEmpty() },
                    routeVersion = result.routeVersion,
                    calibrationVersion = result.calibrationVersion,
                    finished = true,
                    // Preserve Conveyor Profile snapshot from session start.
                    profileNominalSpeedMPerMin = existing?.profileNominalSpeedMPerMin,
                    profileDirection = existing?.profileDirection.orEmpty(),
                    profileExpectedFps = existing?.profileExpectedFps ?: 0f,
                    profileMotionProfile = existing?.profileMotionProfile.orEmpty(),
                ),
            )
            if (events.isNotEmpty()) {
                db.eventDao().insertEvents(events.map { toEntity(result.sessionId, drawingId, it) })
            }
        }
        save(result)
        return summary.copy(conveyorProfile = profileSnap)
    }

    fun loadSession(sessionId: String): PersistedInspectionSession? {
        val db = database() ?: return null
        val entity = db.sessionDao().getSession(sessionId) ?: return null
        val events = db.eventDao().eventsForSession(sessionId).map { it.toPersisted() }
        return PersistedInspectionSession(summary = entity.toSummary(), events = events)
    }

    fun deleteSession(sessionId: String) {
        val db = database() ?: return
        db.eventDao().deleteEventsForSession(sessionId)
        db.sessionDao().deleteSession(sessionId)
        synchronized(lock) {
            results.removeAll { it.sessionId == sessionId }
        }
    }

    fun loadHistory(drawingId: String): List<InspectionSessionSummary> {
        val db = database() ?: return emptyList()
        return db.sessionDao().historyForDrawing(drawingId).map { it.toSummary() }
    }

    fun deleteForDrawing(drawingId: String) {
        val db = database() ?: return
        val sessions = db.sessionDao().historyForDrawing(drawingId).map { it.sessionId }.toSet()
        db.eventDao().deleteEventsForDrawing(drawingId)
        db.sessionDao().deleteSessionsForDrawing(drawingId)
        // Also remove unfinished sessions for drawing
        synchronized(lock) {
            results.removeAll { sessions.contains(it.sessionId) }
        }
    }

    /** Map finished Room sessions to legacy InspectionResult for existing UI. */
    fun historyAsResults(drawingId: String): List<InspectionResult> {
        val db = database() ?: return emptyList()
        return db.sessionDao().historyForDrawing(drawingId).map { it.toInspectionResult() }
    }

    private fun toEntity(sessionId: String, drawingId: String, event: BaseEvent): InspectionEventEntity =
        when (event) {
            is FusionEvent -> InspectionEventEntity(
                sessionId = sessionId,
                drawingId = drawingId,
                timestampNs = event.timestampNs,
                distanceMm = event.distanceMm,
                routePosition = "",
                hasShock = event.shockLevel > 0f,
                shockStrength = event.shockLevel,
                trackingConfidence = event.confidence,
                eventType = "FusionEvent",
            )
            is PositionEvent -> InspectionEventEntity(
                sessionId = sessionId,
                drawingId = drawingId,
                timestampNs = event.timestampNs,
                distanceMm = event.distanceFromSegmentStart,
                routePosition = "${event.segmentId}|${event.nodeId}|${event.progress}",
                hasShock = false,
                shockStrength = 0f,
                trackingConfidence = event.confidence,
                eventType = "PositionEvent",
            )
            else -> InspectionEventEntity(
                sessionId = sessionId,
                drawingId = drawingId,
                timestampNs = event.timestampNs,
                eventType = event.javaClass.simpleName,
            )
        }

    private fun InspectionSessionEntity.toSummary() = InspectionSessionSummary(
        sessionId = sessionId,
        drawingId = drawingId,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        durationMs = durationMs,
        totalDistanceMm = totalDistanceMm,
        shockCount = shockCount,
        averageSpeedMmPerSec = averageSpeedMmPerSec,
        maximumShock = maximumShock,
        coverage = coverage,
        inspectionVersion = inspectionVersion,
        appVersion = appVersion,
        conveyorProfile = toProfileSnapshot(),
    )

    private fun InspectionSessionEntity.toProfileSnapshot() = ConveyorProfileSnapshot(
        nominalSpeedMPerMin = profileNominalSpeedMPerMin,
        direction = runCatching { ConveyorDirection.valueOf(profileDirection) }
            .getOrDefault(ConveyorDirection.FORWARD),
        expectedFps = profileExpectedFps,
        motionProfile = runCatching { ConveyorMotionProfile.valueOf(profileMotionProfile) }
            .getOrDefault(ConveyorMotionProfile.CONSTANT),
    )

    private fun InspectionSessionEntity.toInspectionResult() = InspectionResult(
        sessionId = sessionId,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        durationMs = durationMs,
        routeVersion = routeVersion,
        calibrationVersion = calibrationVersion,
        statistics = InspectionStatistics(
            totalDistanceMm = totalDistanceMm,
            inspectionTimeMs = durationMs,
            shockCount = shockCount,
            averageConfidence = 0f,
            maximumShockLevel = maximumShock,
            minimumConfidence = 0f,
            totalEvents = 0,
            routeVersion = routeVersion,
            calibrationVersion = calibrationVersion,
        ),
        routeQualityScore = coverage,
    )

    private fun InspectionEventEntity.toPersisted() = PersistedInspectionEvent(
        id = id,
        sessionId = sessionId,
        drawingId = drawingId,
        timestampNs = timestampNs,
        distanceMm = distanceMm,
        routePosition = routePosition,
        hasShock = hasShock,
        shockStrength = shockStrength,
        trackingConfidence = trackingConfidence,
        eventType = eventType,
    )

    companion object {
        @Volatile
        private var database: CnvInspectionDatabase? = null

        fun bindDatabase(db: CnvInspectionDatabase) {
            database = db
        }

        fun database(): CnvInspectionDatabase? = database
    }

    private fun database(): CnvInspectionDatabase? = Companion.database()
}
