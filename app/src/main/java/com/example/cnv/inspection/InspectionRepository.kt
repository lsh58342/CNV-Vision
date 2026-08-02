package com.example.cnv.inspection

import android.os.Looper
import com.example.cnv.core.event.BaseEvent
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.PositionEvent
import com.example.cnv.factory.model.ConveyorDirection
import com.example.cnv.factory.model.ConveyorMotionProfile
import com.example.cnv.factory.model.ConveyorProfileConfig
import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.inspection.db.CnvInspectionDatabase
import com.example.cnv.inspection.db.InspectionEventEntity
import com.example.cnv.inspection.db.InspectionSessionEntity
import com.example.cnv.speed.SpeedValidationSummary

/**
 * Inspection session store — in-memory cache + Room persistence.
 * STEP 15-4: all Room DAO calls must run off the main thread ([ensureBackground]).
 */
class InspectionRepository(
    private val limit: Int = InspectionConfig.DEFAULT_CACHE_LIMIT,
) {

    private val lock = Any()
    private val results = ArrayDeque<InspectionResult>()

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
        ruleCatalogVersion: Int = 0,
    ) {
        ensureBackground()
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
                profileSpeedTolerancePercent = conveyorProfile.speedTolerancePercent,
                profileDirection = conveyorProfile.direction.name,
                profileExpectedFps = conveyorProfile.expectedFps,
                profileMotionProfile = conveyorProfile.motionProfile.name,
                ruleCatalogVersion = ruleCatalogVersion,
            ),
        )
    }

    fun appendEvent(
        sessionId: String,
        drawingId: String,
        event: BaseEvent,
    ) {
        ensureBackground()
        val db = database() ?: return
        db.eventDao().insertEvent(toEntity(sessionId, drawingId, event))
    }

    fun appendEvents(
        sessionId: String,
        drawingId: String,
        events: List<BaseEvent>,
    ) {
        ensureBackground()
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
        speedValidation: SpeedValidationSummary = SpeedValidationSummary.EMPTY,
        conveyorProfile: ConveyorProfileSnapshot? = null,
    ): InspectionSessionSummary {
        ensureBackground()
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
            speedValidation = speedValidation,
        )
        val db = database()
        var profileSnap = conveyorProfile ?: ConveyorProfileSnapshot.empty()
        var ruleVersion = 0
        if (db != null) {
            val existing = db.sessionDao().getSession(result.sessionId)
            profileSnap = existing?.toProfileSnapshot() ?: profileSnap
            ruleVersion = existing?.ruleCatalogVersion ?: 0
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
                    profileNominalSpeedMPerMin = existing?.profileNominalSpeedMPerMin
                        ?: profileSnap.nominalSpeedMPerMin,
                    profileSpeedTolerancePercent = existing?.profileSpeedTolerancePercent
                        ?: profileSnap.speedTolerancePercent,
                    profileDirection = existing?.profileDirection?.takeIf { it.isNotBlank() }
                        ?: profileSnap.direction.name,
                    profileExpectedFps = existing?.profileExpectedFps?.takeIf { it > 0f }
                        ?: profileSnap.expectedFps,
                    profileMotionProfile = existing?.profileMotionProfile?.takeIf { it.isNotBlank() }
                        ?: profileSnap.motionProfile.name,
                    avgExpectedSpeedMPerMin = speedValidation.averageExpectedSpeedMPerMin,
                    avgMeasuredSpeedMPerMin = speedValidation.averageMeasuredSpeedMPerMin,
                    maxSpeedDifferenceMm = speedValidation.maximumDifferenceMm,
                    avgSpeedDifferenceMm = speedValidation.averageDifferenceMm,
                    speedValidationScore = speedValidation.validationScore,
                    ruleCatalogVersion = ruleVersion,
                ),
            )
            if (events.isNotEmpty()) {
                db.eventDao().insertEvents(events.map { toEntity(result.sessionId, drawingId, it) })
            }
        }
        save(result)
        return summary.copy(conveyorProfile = profileSnap, ruleCatalogVersion = ruleVersion)
    }

    fun loadSession(sessionId: String): PersistedInspectionSession? {
        ensureBackground()
        val db = database() ?: return null
        val entity = db.sessionDao().getSession(sessionId) ?: return null
        val events = db.eventDao().eventsForSession(sessionId).map { it.toPersisted() }
        return PersistedInspectionSession(summary = entity.toSummary(), events = events)
    }

    fun deleteSession(sessionId: String) {
        ensureBackground()
        val db = database() ?: return
        db.eventDao().deleteEventsForSession(sessionId)
        db.sessionDao().deleteSession(sessionId)
        synchronized(lock) {
            results.removeAll { it.sessionId == sessionId }
        }
    }

    fun loadHistory(drawingId: String): List<InspectionSessionSummary> {
        ensureBackground()
        val db = database() ?: return emptyList()
        return db.sessionDao().historyForDrawing(drawingId).map { it.toSummary() }
    }

    fun deleteForDrawing(drawingId: String) {
        ensureBackground()
        val db = database() ?: return
        val sessions = db.sessionDao().historyForDrawing(drawingId).map { it.sessionId }.toSet()
        db.eventDao().deleteEventsForDrawing(drawingId)
        db.sessionDao().deleteSessionsForDrawing(drawingId)
        synchronized(lock) {
            results.removeAll { sessions.contains(it.sessionId) }
        }
    }

    fun historyAsResults(drawingId: String): List<InspectionResult> {
        ensureBackground()
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
        speedValidation = SpeedValidationSummary(
            sampleCount = 0,
            averageExpectedSpeedMPerMin = avgExpectedSpeedMPerMin,
            averageMeasuredSpeedMPerMin = avgMeasuredSpeedMPerMin,
            maximumDifferenceMm = maxSpeedDifferenceMm,
            averageDifferenceMm = avgSpeedDifferenceMm,
            validationScore = speedValidationScore,
        ),
        ruleCatalogVersion = ruleCatalogVersion,
    )

    private fun InspectionSessionEntity.toProfileSnapshot() = ConveyorProfileSnapshot(
        nominalSpeedMPerMin = profileNominalSpeedMPerMin,
        direction = runCatching { ConveyorDirection.valueOf(profileDirection) }
            .getOrDefault(ConveyorDirection.FORWARD),
        expectedFps = profileExpectedFps,
        motionProfile = runCatching { ConveyorMotionProfile.valueOf(profileMotionProfile) }
            .getOrDefault(ConveyorMotionProfile.CONSTANT),
        speedTolerancePercent = profileSpeedTolerancePercent
            .takeIf { it > 0f }
            ?: ConveyorProfileConfig.DEFAULT_SPEED_TOLERANCE_PERCENT,
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

        fun ensureBackground() {
            check(Looper.myLooper() != Looper.getMainLooper()) {
                "Room access on main thread is forbidden (STEP 15-4)"
            }
        }
    }

    private fun database(): CnvInspectionDatabase? = Companion.database()

    private fun ensureBackground() = Companion.ensureBackground()
}
