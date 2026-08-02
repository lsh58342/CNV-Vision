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
import com.example.cnv.report.excel.ExcelArchiveEntry
import com.example.cnv.speed.SpeedValidationSummary

/**
 * Inspection session store — in-memory cache + Room persistence.
 * STEP 15-4: all Room DAO calls must run off the main thread ([ensureBackground]).
 * STEP 20-3: route / analysis / rule / heat / excel session artifacts.
 */
class InspectionRepository(
    private val limit: Int = InspectionConfig.DEFAULT_CACHE_LIMIT,
) {

    private val lock = Any()
    private val results = ArrayDeque<InspectionResult>()

    /** Carry-forward Route position for Fusion → Shock Event enrichment (STEP 20-20). */
    private var lastRouteLabel: String = ""
    private var lastSegmentId: String = ""
    private var lastProgress: Float = 0f
    private var lastRouteMm: Float = 0f
    private var lastWorldX: Float = 0f
    private var lastWorldY: Float = 0f
    private var lastZoneName: String = ""
    private var lastTimestampNs: Long = 0L
    private var lastSpeedMmPerSec: Float = 0f
    private val recentShockG = ArrayDeque<Float>()
    private val movingAvgWindow: Int = 5

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
        inspectionProfileJson: String = "",
        routeSnapshotJson: String = "",
    ) {
        ensureBackground()
        resetEventEnrichment()
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
                inspectionProfileJson = inspectionProfileJson,
                routeSnapshotJson = routeSnapshotJson,
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
        analysisResultJson: String = "",
        ruleResultJson: String = "",
        heatPointsJson: String = "",
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
        var profileJson = ""
        var routeJson = ""
        var excelUri = ""
        var excelName = ""
        if (db != null) {
            val existing = db.sessionDao().getSession(result.sessionId)
            profileSnap = existing?.toProfileSnapshot() ?: profileSnap
            ruleVersion = existing?.ruleCatalogVersion ?: 0
            profileJson = existing?.inspectionProfileJson.orEmpty()
            routeJson = existing?.routeSnapshotJson.orEmpty()
            excelUri = existing?.excelFileUri.orEmpty()
            excelName = existing?.excelFileName.orEmpty()
            val analysisJson = analysisResultJson.ifBlank {
                existing?.analysisResultJson.orEmpty()
            }
            val ruleJson = ruleResultJson.ifBlank {
                existing?.ruleResultJson.orEmpty()
            }
            val heatJson = heatPointsJson.ifBlank {
                existing?.heatPointsJson.orEmpty()
            }
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
                    inspectionProfileJson = profileJson,
                    routeSnapshotJson = routeJson,
                    analysisResultJson = analysisJson,
                    ruleResultJson = ruleJson,
                    heatPointsJson = heatJson,
                    excelFileUri = excelUri,
                    excelFileName = excelName,
                ),
            )
            if (events.isNotEmpty()) {
                db.eventDao().insertEvents(events.map { toEntity(result.sessionId, drawingId, it) })
            }
        }
        save(result)
        return summary.copy(
            conveyorProfile = profileSnap,
            ruleCatalogVersion = ruleVersion,
            inspectionProfileJson = profileJson,
            routeSnapshotJson = routeJson,
            analysisResultJson = analysisResultJson,
            ruleResultJson = ruleResultJson,
            heatPointsJson = heatPointsJson,
            excelFileUri = excelUri,
            excelFileName = excelName,
        )
    }

    fun saveExcelArchive(entry: ExcelArchiveEntry) {
        ensureBackground()
        val db = database() ?: return
        val existing = db.sessionDao().getSession(entry.sessionId) ?: return
        db.sessionDao().insertSession(
            existing.copy(
                excelFileUri = entry.fileUri,
                excelFileName = entry.fileName,
            ),
        )
    }

    fun loadExcelArchive(sessionId: String): ExcelArchiveEntry? {
        ensureBackground()
        val db = database() ?: return null
        val entity = db.sessionDao().getSession(sessionId) ?: return null
        if (entity.excelFileUri.isBlank()) return null
        return ExcelArchiveEntry(
            sessionId = entity.sessionId,
            drawingId = entity.drawingId,
            fileUri = entity.excelFileUri,
            fileName = entity.excelFileName.ifBlank { "report.xlsx" },
        )
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

    private fun resetEventEnrichment() {
        lastRouteLabel = ""
        lastSegmentId = ""
        lastProgress = 0f
        lastRouteMm = 0f
        lastWorldX = 0f
        lastWorldY = 0f
        lastZoneName = ""
        lastTimestampNs = 0L
        lastSpeedMmPerSec = 0f
        recentShockG.clear()
    }

    private fun toEntity(sessionId: String, drawingId: String, event: BaseEvent): InspectionEventEntity =
        when (event) {
            is FusionEvent -> {
                val shockG = com.example.cnv.imu.ShockUnits.ms2ToG(
                    maxOf(event.peakAcceleration, event.shockLevel),
                )
                val recordable = com.example.cnv.imu.ShockUnits.isRecordableG(shockG)
                val avgG = if (recordable) {
                    recentShockG.addLast(shockG)
                    while (recentShockG.size > movingAvgWindow) recentShockG.removeFirst()
                    recentShockG.average().toFloat()
                } else {
                    recentShockG.average().toFloat().takeIf { recentShockG.isNotEmpty() } ?: 0f
                }
                if (recordable) {
                    println(
                        "LOG[ShockEvent][SAVE] session=$sessionId " +
                            "ts=${event.timestampNs} routeMm=$lastRouteMm " +
                            "world=(${"%.1f".format(lastWorldX)},${"%.1f".format(lastWorldY)}) " +
                            "speed=${"%.1f".format(lastSpeedMmPerSec)} " +
                            "shockG=${"%.2f".format(shockG)} peakG=${"%.2f".format(shockG)} " +
                            "avgG=${"%.2f".format(avgG)} zone=$lastZoneName",
                    )
                }
                InspectionEventEntity(
                    sessionId = sessionId,
                    drawingId = drawingId,
                    timestampNs = event.timestampNs,
                    distanceMm = if (lastRouteMm > 0f) lastRouteMm else event.distanceMm,
                    routePosition = lastRouteLabel,
                    hasShock = recordable,
                    shockStrength = if (recordable) shockG else 0f,
                    trackingConfidence = event.confidence,
                    eventType = "FusionEvent",
                    routePositionMm = lastRouteMm,
                    worldX = lastWorldX,
                    worldY = lastWorldY,
                    speedMmPerSec = lastSpeedMmPerSec,
                    peakG = if (recordable) shockG else 0f,
                    movingAverageG = avgG,
                    zoneName = lastZoneName,
                )
            }
            is PositionEvent -> {
                val label = "${event.segmentId}|${event.nodeId}|${event.progress}"
                lastRouteLabel = label
                lastSegmentId = event.segmentId
                lastProgress = event.progress
                val resolved = InspectionShockGeo.resolve(event.segmentId, event.progress)
                if (resolved != null) {
                    if (lastTimestampNs > 0L && event.timestampNs > lastTimestampNs) {
                        val dt = (event.timestampNs - lastTimestampNs) / 1_000_000_000.0
                        if (dt > 0.0) {
                            lastSpeedMmPerSec =
                                ((resolved.routePositionMm - lastRouteMm) / dt).toFloat()
                        }
                    }
                    lastRouteMm = resolved.routePositionMm
                    lastWorldX = resolved.worldX
                    lastWorldY = resolved.worldY
                    lastZoneName = resolved.zoneName
                } else {
                    lastRouteMm = event.distanceFromSegmentStart
                }
                lastTimestampNs = event.timestampNs
                InspectionEventEntity(
                    sessionId = sessionId,
                    drawingId = drawingId,
                    timestampNs = event.timestampNs,
                    distanceMm = lastRouteMm,
                    routePosition = label,
                    hasShock = false,
                    shockStrength = 0f,
                    trackingConfidence = event.confidence,
                    eventType = "PositionEvent",
                    routePositionMm = lastRouteMm,
                    worldX = lastWorldX,
                    worldY = lastWorldY,
                    speedMmPerSec = lastSpeedMmPerSec,
                    peakG = 0f,
                    movingAverageG = 0f,
                    zoneName = lastZoneName,
                )
            }
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
        inspectionProfileJson = inspectionProfileJson,
        routeSnapshotJson = routeSnapshotJson,
        analysisResultJson = analysisResultJson,
        ruleResultJson = ruleResultJson,
        heatPointsJson = heatPointsJson,
        excelFileUri = excelFileUri,
        excelFileName = excelFileName,
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
        routePositionMm = routePositionMm,
        worldX = worldX,
        worldY = worldY,
        speedMmPerSec = speedMmPerSec,
        peakG = peakG,
        movingAverageG = movingAverageG,
        zoneName = zoneName,
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
