package com.example.cnv.ui.screen.inspection

import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.example.cnv.camera.CameraManager
import com.example.cnv.config.CalibrationManager
import com.example.cnv.core.config.IMUConfig
import com.example.cnv.debug.RouteDebugController
import com.example.cnv.debug.RouteDebugView
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.fusion.FusionEngine
import com.example.cnv.imu.IMUManager
import com.example.cnv.heatmap.HeatMapRouteLayout
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.inspection.InspectionState
import com.example.cnv.inspection.RouteQualityScore
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.opencv.OpenCVManager
import com.example.cnv.production.ProductionMetrics
import com.example.cnv.route.RouteGenerator
import com.example.cnv.rule.RuleSeverity
import com.example.cnv.speed.SpeedValidatorEngine
import com.example.cnv.zone.editor.ZonePolylineResolver
import com.example.cnv.core.model.RouteDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2

/**
 * Inspection UI bridge — wires existing Camera / OpenCV / Fusion / Inspection engines.
 * Does not change domain algorithms (UI Rebuild Phase 3).
 * STEP 15-2: SpeedValidatorEngine validates Nominal Speed vs Measured Distance only.
 */
class InspectionPipeline(
    private val activity: AppCompatActivity,
) {

    private val catalog = FactoryCatalog.get()

    private val bootstrapGray = ImageView(activity).apply { visibility = View.GONE }
    private val bootstrapPreview = PreviewView(activity).apply { visibility = View.GONE }

    private val openCvManager = OpenCVManager(activity, bootstrapGray)
    private val cameraManager = CameraManager(activity, bootstrapPreview)
    private val imuManager = IMUManager(activity)
    private val fusionEngine = FusionEngine(
        initialCalibrated = CalibrationManager.getInstance(activity).isCalibrated(),
    )
    private val speedValidatorEngine = SpeedValidatorEngine(
        profileProvider = { catalog.drawings.current()?.conveyorProfile },
    ).also { SpeedValidatorEngine.bindShared(it) }
    private val routeRepository = catalog.routes.underlying()
    private val routeGenerator = RouteGenerator(routeRepository = routeRepository)
    private val mapMatchingEngine: MapMatchingEngine
    private val inspectionManager: InspectionManager
    private val routeDebugController: RouteDebugController

    private var cameraRunning = false
    private var sensorsRunning = false
    private var detached = false

    /** Frozen at START — finishSession uses this, not live Drawing profile. */
    private var sessionConveyorSnapshot: ConveyorProfileSnapshot? = null

    /** Cached route layout for dashboard coordinate / coverage display (built once). */
    private var dashboardLayout: HeatMapRouteLayout.LayoutResult? = null

    /** Lightweight live shock display aggregates from FusionResult (not Analysis Engine). */
    private var liveMaxShock: Float = 0f
    private var liveShockSum: Float = 0f
    private var liveShockSamples: Int = 0
    private var liveShockCount: Int = 0
    private var liveDistanceMm: Float = 0f
    private var lastFusionTimestampNs: Long = 0L

    /** Segments fully passed during the active / last session (UI overlay only). */
    private val traversedSegmentIds = linkedSetOf<String>()
    private var lastMarkerSegmentId: String? = null
    private var lastDirectionRad: Float = 0f
    private var lastOverlayMarkerX: Double? = null
    private var lastOverlayMarkerY: Double? = null

    init {
        // Route must already exist from Commissioning — never auto-generate sample routes.
        mapMatchingEngine = MapMatchingEngine(routeRepository = routeRepository)
        inspectionManager = InspectionManager(repository = catalog.inspections.underlying())
        val stubRouteView = RouteDebugView(activity).apply { visibility = View.GONE }
        routeDebugController = RouteDebugController(
            routeRepository = routeRepository,
            routeDebugView = stubRouteView,
            statsTextView = TextView(activity),
            issuesTextView = TextView(activity),
            mapMatchingEngine = mapMatchingEngine,
            mapperProvider = { routeGenerator.latestResult()?.mapper },
        )
    }

    fun speedValidator(): SpeedValidatorEngine = speedValidatorEngine

    fun attachPreview(preview: PreviewView) {
        detached = false
        cameraManager.attachPreviewView(preview)
        startSensors()
        if (!cameraRunning) {
            val analyzer = openCvManager.start()
            cameraManager.start(analyzer)
            cameraRunning = true
        }
        bindProductionWatchdog()
    }

    fun detach() {
        if (detached) return
        detached = true
        unbindProductionWatchdog()
        if (inspectionManager.state() == InspectionState.RUNNING) {
            stopSession()
        }
        if (cameraRunning) {
            cameraManager.stop()
            openCvManager.release()
            cameraRunning = false
        }
        stopSensors()
    }

    private fun bindProductionWatchdog() {
        val watchdog = com.example.cnv.production.ProductionWatchdog.shared()
        com.example.cnv.production.RecoveryCoordinator.registerCameraReinit {
            if (!detached && cameraRunning) {
                cameraManager.reinitialize()
            }
        }
        watchdog.setListener(
            object : com.example.cnv.production.ProductionWatchdog.Listener {
                override fun onCameraStall() {
                    com.example.cnv.production.RecoveryCoordinator.recoverCamera("stall")
                }
                override fun onSensorStall() = Unit
                override fun onReplayStall() = Unit
                override fun onFrameProcessingStall() {
                    com.example.cnv.production.RecoveryCoordinator.recoverCamera("process_stall")
                }
            },
        )
        watchdog.setCameraExpected(true)
        watchdog.setSensorExpected(true)
        watchdog.setReplayExpected(false)
        watchdog.start()
    }

    private fun unbindProductionWatchdog() {
        val watchdog = com.example.cnv.production.ProductionWatchdog.shared()
        watchdog.setCameraExpected(false)
        watchdog.setSensorExpected(false)
        watchdog.setListener(null)
        com.example.cnv.production.RecoveryCoordinator.registerCameraReinit(null)
        if (!watchdogHasOtherClients()) {
            watchdog.stop()
        }
    }

    private fun watchdogHasOtherClients(): Boolean = false

    fun startSession(): Boolean {
        val route = routeRepository.current() ?: return false
        val calibration = CalibrationManager.getInstance(activity)
        val calData = calibration.getCalibrationData()
        val quality = RouteQualityScore.from(routeDebugController.latestValidation())
        val versionName = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0" }
        val session = inspectionManager.start(
            InspectionManager.StartRequest(
                route = route,
                calibrationVersion = calData?.version ?: 0,
                calibrationValue = calibration.getMmPerPixel(),
                appVersion = versionName,
                deviceInformation = "${Build.MANUFACTURER} ${Build.MODEL}",
                samplingRateHz = 1_000_000f / IMUConfig.DEFAULT_SAMPLING_PERIOD_US,
                routeQualityScore = quality,
            ),
        )
        val drawingId = CurrentContext.get().drawingId
        val drawing = drawingId?.let { catalog.drawings.get(it) }
        // Freeze Conveyor Profile for Speed Validation (session-scoped snapshot).
        speedValidatorEngine.beginSession(drawing?.conveyorProfile)
        val profileSnap = drawing?.conveyorProfile?.let { ConveyorProfileSnapshot.from(it) }
            ?: ConveyorProfileSnapshot.empty()
        sessionConveyorSnapshot = profileSnap
        resetLiveAggregates()
        traversedSegmentIds.clear()
        lastMarkerSegmentId = null
        ensureDashboardLayout()
        if (drawingId != null) {
            val conveyorLive = drawing?.conveyorProfile
            val sid = session.sessionId
            val did = drawingId
            val routeSnapshotJson = com.example.cnv.inspection.RouteSnapshotCodec.encode(
                session.routeSnapshot,
            )
            // Bind stream on session thread before first events accumulate.
            session.recorder().bindStream { chunk ->
                InspectionDbGate.execute {
                    catalog.inspections.appendEvents(sid, did, chunk)
                }
            }
            InspectionDbGate.execute {
                val stored = catalog.inspectionProfiles.loadSync(
                    drawingId,
                    conveyorFallback = conveyorLive
                        ?: com.example.cnv.factory.model.ConveyorProfile.fromConfig(),
                )
                val profile = stored.copy(
                    conveyor = conveyorLive ?: stored.conveyor,
                    rule = if (stored.rule.entries.isEmpty()) {
                        com.example.cnv.factory.repository.InspectionProfileRepository
                            .buildDefaultRuleProfile(catalog.rules.catalogVersion())
                    } else {
                        stored.rule.copy(catalogVersion = catalog.rules.catalogVersion())
                    },
                    updatedAtMs = System.currentTimeMillis(),
                )
                val snapshot = com.example.cnv.profile.InspectionProfileSnapshot.from(profile)
                catalog.inspections.createSession(
                    drawingId = drawingId,
                    sessionId = session.sessionId,
                    startTimeMs = session.startTimeMs,
                    appVersion = versionName,
                    routeVersion = session.freeze.routeVersion,
                    calibrationVersion = session.freeze.calibrationVersion,
                    conveyorProfile = profileSnap,
                    ruleCatalogVersion = catalog.rules.catalogVersion(),
                    inspectionProfileJson = com.example.cnv.profile.InspectionProfileCodec
                        .encodeSnapshot(snapshot),
                    routeSnapshotJson = routeSnapshotJson,
                )
                // Same background thread — avoid nested DbGate submit.
                catalog.inspectionProfiles.saveSync(drawingId, profile)
            }
        }
        return true
    }

    fun stopSession(): InspectionResult? {
        val active = inspectionManager.currentSession()
        val sessionRoute = active?.routeSnapshot
        val sessionId = active?.sessionId
        // Drain remaining recorder chunk to Room before stop (avoid duplicate bulk insert).
        active?.recorder()?.flushPending()
        val appVersion = active?.freeze?.appVersion.orEmpty()
        val profileSnap = sessionConveyorSnapshot
        sessionConveyorSnapshot = null
        val speedSummary = speedValidatorEngine.endSession()
        val result = inspectionManager.stop()
        val drawingId = CurrentContext.get().drawingId
        if (result != null && drawingId != null && sessionId != null) {
            val resolvedAppVersion = appVersion.ifBlank {
                runCatching {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                }.getOrNull().orEmpty().ifBlank { "1.0" }
            }
            val routeForHeat = sessionRoute?.toRoute()
            val mapper = routeGenerator.latestResult()?.mapper
            InspectionDbGate.execute {
                val persisted = catalog.inspections.loadSession(sessionId)
                val heatPoints = if (persisted != null && routeForHeat != null) {
                    catalog.heatMaps.generateSessionPoints(
                        drawingId = drawingId,
                        session = persisted,
                        route = routeForHeat,
                        mapper = mapper,
                    )
                } else {
                    emptyList()
                }
                val heatJson = com.example.cnv.heatmap.HeatPointsCodec.encode(heatPoints)
                val analysis = catalog.analysis.analyzeAndPersistSync(
                    sessionId = sessionId,
                    preferredDrawingId = drawingId,
                    routeOverride = routeForHeat,
                    heatPointsOverride = heatPoints,
                )
                val rules = if (analysis != null) {
                    catalog.rules.evaluateAndPersistSync(
                        sessionId = sessionId,
                        preferredDrawingId = drawingId,
                        analysisOverride = analysis,
                    )
                } else {
                    null
                }
                catalog.inspections.finishSession(
                    drawingId = drawingId,
                    result = result,
                    events = emptyList(),
                    appVersion = resolvedAppVersion,
                    speedValidation = speedSummary,
                    conveyorProfile = profileSnap,
                    analysisResultJson = analysis?.let {
                        com.example.cnv.analysis.AnalysisResultCodec.encode(it)
                    }.orEmpty(),
                    ruleResultJson = rules?.let {
                        com.example.cnv.rule.RuleResultCodec.encode(it)
                    }.orEmpty(),
                    heatPointsJson = heatJson,
                )
                if (routeForHeat != null) {
                    catalog.heatMaps.regenerateHeatLayer(
                        drawingId = drawingId,
                        inspectionRepository = catalog.inspections.underlying(),
                        route = routeForHeat,
                        mapper = mapper,
                    )
                }
            }
        }
        return result
    }

    /**
     * Live Route overlay snapshot — same drawing coordinates as Dashboard.
     * Reads RouteRepository / MapMatching / CurrentContext only (no engine mutation).
     */
    fun readLiveRouteOverlay(): LiveRouteOverlayState {
        val route = routeRepository.current()
        val layout = ensureDashboardLayout()
        val position = mapMatchingEngine.latestPosition()
        val running = inspectionManager.state() == InspectionState.RUNNING
        val tracking = when {
            !running && position == null && lastOverlayMarkerX == null -> LiveTrackingVisual.STOPPED
            !running -> LiveTrackingVisual.STOPPED
            position == null -> LiveTrackingVisual.SEARCHING
            position.confidence < 0.25f -> LiveTrackingVisual.LOST
            else -> LiveTrackingVisual.GOOD
        }

        if (route == null || layout == null) {
            return LiveRouteOverlayState(
                markerX = lastOverlayMarkerX,
                markerY = lastOverlayMarkerY,
                directionRad = lastDirectionRad,
                tracking = tracking,
            )
        }

        val ordered = layout.segmentStartMm.entries.sortedBy { it.value }.map { it.key }
        val segments = ordered.mapNotNull { id ->
            val start = HeatMapRouteLayout.toDrawingCoordinate(layout, id, 0f) ?: return@mapNotNull null
            val end = HeatMapRouteLayout.toDrawingCoordinate(layout, id, 1f) ?: return@mapNotNull null
            LiveRouteSegmentDraw(id, start.x, start.y, end.x, end.y)
        }

        if (position != null) {
            val prevId = lastMarkerSegmentId
            if (prevId != null && prevId != position.segmentId) {
                traversedSegmentIds.add(prevId)
            }
            lastMarkerSegmentId = position.segmentId
            val world = HeatMapRouteLayout.toDrawingCoordinate(
                layout,
                position.segmentId,
                position.progress,
            )
            if (world != null) {
                val prevX = lastOverlayMarkerX
                val prevY = lastOverlayMarkerY
                if (prevX != null && prevY != null) {
                    val dx = world.x - prevX
                    val dy = world.y - prevY
                    if (dx * dx + dy * dy > 1e-6) {
                        lastDirectionRad = atan2(dy, dx).toFloat()
                    }
                } else {
                    val seg = segments.firstOrNull { it.id == position.segmentId }
                    if (seg != null) {
                        val dx = seg.endX - seg.startX
                        val dy = seg.endY - seg.startY
                        var rad = atan2(dy, dx).toFloat()
                        if (position.direction == RouteDirection.BACKWARD) {
                            rad += Math.PI.toFloat()
                        }
                        lastDirectionRad = rad
                    }
                }
                lastOverlayMarkerX = world.x
                lastOverlayMarkerY = world.y
            }
        }

        val ctx = CurrentContext.get()
        val drawing = ctx.drawingId?.let { catalog.drawings.get(it) } ?: catalog.drawings.current(ctx)
        var originX: Double? = null
        var originY: Double? = null
        if (drawing?.originSet == true) {
            val progress = (drawing.originX ?: 0f).coerceIn(0f, 1f)
            val startSeg = route.startSegmentId ?: ordered.firstOrNull()
            if (startSeg != null) {
                val o = HeatMapRouteLayout.toDrawingCoordinate(layout, startSeg, progress)
                originX = o?.x
                originY = o?.y
            }
        }

        val zone = catalog.zones.current(ctx)
            ?: drawing?.id?.let { did ->
                catalog.zones.forDrawing(did).firstOrNull { z ->
                    z.name.equals(
                        resolveZoneName(
                            if (position != null) {
                                HeatMapRouteLayout.absoluteRouteMm(
                                    layout,
                                    position.segmentId,
                                    position.progress,
                                ) ?: 0f
                            } else {
                                0f
                            },
                            layout,
                        ),
                        ignoreCase = true,
                    )
                }
            }
        val zoneIds = if (zone != null) {
            ZonePolylineResolver.resolvedIds(zone, route).toSet()
        } else {
            emptySet()
        }

        return LiveRouteOverlayState(
            segments = segments,
            traversedSegmentIds = traversedSegmentIds.toSet(),
            currentSegmentId = position?.segmentId ?: lastMarkerSegmentId,
            currentProgress = position?.progress ?: 0f,
            markerX = lastOverlayMarkerX,
            markerY = lastOverlayMarkerY,
            directionRad = lastDirectionRad,
            originX = originX,
            originY = originY,
            zoneSegmentIds = zoneIds,
            tracking = tracking,
        )
    }

    fun readStatus(): InspectionUiStatus = readLiveDashboard().toUiStatus()

    /**
     * Live Dashboard snapshot — Repository / Engine reads only (STEP 20-1).
     * Does not run Analysis, Rule evaluation, HeatMap, or Distance algorithms.
     */
    fun readLiveDashboard(): LiveInspectionDashboardState {
        val fusion = fusionEngine.repository.latest()
        val position = mapMatchingEngine.latestPosition()
        val session = inspectionManager.currentSession()
        val state = inspectionManager.state()
        val running = state == InspectionState.RUNNING
        val sample = speedValidatorEngine.latest()
        val speedSummary = speedValidatorEngine.sessionSummary()
        val layout = ensureDashboardLayout()

        if (fusion != null && fusion.timestampNs != lastFusionTimestampNs) {
            lastFusionTimestampNs = fusion.timestampNs
            liveDistanceMm += kotlin.math.abs(fusion.distance)
            if (fusion.shockLevel > 0f) {
                liveShockCount++
                liveShockSum += fusion.shockLevel
                liveShockSamples++
                if (fusion.shockLevel > liveMaxShock) liveMaxShock = fusion.shockLevel
            }
        }

        val trackingLabel = when {
            running && position != null -> "LOCKED"
            running -> "SEARCHING"
            else -> state.name
        }
        val routeMm = if (position != null && layout != null) {
            HeatMapRouteLayout.absoluteRouteMm(layout, position.segmentId, position.progress) ?: 0f
        } else {
            liveDistanceMm
        }
        val world = if (position != null && layout != null) {
            HeatMapRouteLayout.toDrawingCoordinate(layout, position.segmentId, position.progress)
        } else {
            null
        }
        val coverage = if (layout != null && layout.totalLengthMm > 0f) {
            (routeMm / layout.totalLengthMm).coerceIn(0f, 1f)
        } else {
            0f
        }
        val zoneName = resolveZoneName(routeMm, layout)
        val ctx = CurrentContext.get()
        val drawing = ctx.drawingId?.let { catalog.drawings.get(it) } ?: catalog.drawings.current(ctx)
        val floor = drawing?.floorId?.let { catalog.floors.get(it) }
            ?: ctx.floorId?.let { catalog.floors.get(it) }
        val building = floor?.buildingId?.let { catalog.buildings.get(it) }
            ?: ctx.buildingId?.let { catalog.buildings.get(it) }
        val timeLabel = if (session != null) {
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(session.startTimeMs))
        } else {
            "—"
        }
        val elapsedSec = if (session != null && running) session.elapsedMs() / 1000.0 else 0.0
        val avgShock = if (liveShockSamples > 0) liveShockSum / liveShockSamples else 0f
        val confidence = position?.confidence
            ?: fusion?.confidence
            ?: sample?.validatedFusionConfidence
            ?: 0f
        val speedDiff = if (sample != null) {
            sample.measuredSpeedMPerMin - sample.nominalSpeedMPerMin
        } else {
            null
        }
        val warnings = buildWarnings(sample != null && speedValidatorEngine.mismatchWarning())
        return LiveInspectionDashboardState(
            buildingName = building?.name ?: "—",
            floorName = floor?.name ?: "—",
            drawingName = drawing?.name ?: "—",
            inspectionTimeLabel = timeLabel,
            elapsedSec = elapsedSec,
            currentZoneName = zoneName,
            routePositionMm = routeMm,
            coordinateX = world?.x?.toFloat(),
            coordinateY = world?.y?.toFloat(),
            currentSpeedMPerMin = sample?.measuredSpeedMPerMin,
            nominalSpeedMPerMin = sample?.nominalSpeedMPerMin
                ?: drawing?.conveyorProfile?.nominalSpeedMPerMin,
            speedDifferenceMPerMin = speedDiff,
            currentShock = fusion?.shockLevel ?: 0f,
            maximumShock = liveMaxShock,
            averageShock = avgShock,
            trackingConfidence = confidence,
            coverage = coverage,
            validationScore = speedSummary.validationScore,
            trackingLabel = trackingLabel,
            sessionState = state.name,
            running = running,
            system = buildSystemStatus(running, trackingLabel, fusion != null),
            warnings = warnings,
            distanceMm = routeMm,
            shockCount = liveShockCount,
            speedMismatchWarning = speedValidatorEngine.mismatchWarning(),
        )
    }

    private fun buildSystemStatus(
        running: Boolean,
        trackingLabel: String,
        hasFusion: Boolean,
    ): SystemStatusSnapshot {
        val metrics = ProductionMetrics.snapshot()
        fun fromAge(ageMs: Double, expected: Boolean): SystemModuleState = when {
            !expected -> SystemModuleState.READY
            ageMs.isInfinite() || ageMs > 5_000 -> SystemModuleState.ERROR
            ageMs > 2_000 -> SystemModuleState.WARNING
            running -> SystemModuleState.RUNNING
            else -> SystemModuleState.READY
        }
        val trackingState = when {
            !running -> SystemModuleState.READY
            trackingLabel == "LOCKED" -> SystemModuleState.RUNNING
            trackingLabel == "SEARCHING" -> SystemModuleState.WARNING
            else -> SystemModuleState.READY
        }
        val roomState = when {
            metrics.roomRetries > 5 -> SystemModuleState.ERROR
            metrics.roomRetries > 0 -> SystemModuleState.WARNING
            else -> SystemModuleState.READY
        }
        return SystemStatusSnapshot(
            camera = fromAge(metrics.cameraStallMs, cameraRunning),
            openCv = fromAge(metrics.processStallMs, cameraRunning),
            tracking = trackingState,
            fusion = when {
                !sensorsRunning -> SystemModuleState.READY
                !hasFusion && running -> SystemModuleState.WARNING
                running -> SystemModuleState.RUNNING
                else -> SystemModuleState.READY
            },
            replay = SystemModuleState.READY,
            room = roomState,
        )
    }

    private fun buildWarnings(speedMismatch: Boolean): List<LiveDashboardWarning> {
        val out = ArrayList<LiveDashboardWarning>()
        if (speedMismatch) {
            out += LiveDashboardWarning("Speed", "Nominal vs measured mismatch")
        }
        // Cached Rule Result only — never re-evaluate.
        val sessionId = inspectionManager.currentSession()?.sessionId
        val cached = sessionId?.let { catalog.rules.getCached(it) }
        cached?.triggered().orEmpty()
            .filter {
                it.severity == RuleSeverity.CRITICAL ||
                    it.severity == RuleSeverity.HIGH ||
                    it.severity == RuleSeverity.MEDIUM
            }
            .forEach { hit ->
                out += LiveDashboardWarning(hit.ruleId, hit.description)
            }
        val metrics = ProductionMetrics.snapshot()
        if (metrics.cameraStallMs.isFinite() && metrics.cameraStallMs > 3_000 && cameraRunning) {
            out += LiveDashboardWarning("Camera", "Frame stall")
        }
        return out
    }

    private fun resolveZoneName(
        routeMm: Float,
        layout: HeatMapRouteLayout.LayoutResult?,
    ): String {
        val ctx = CurrentContext.get()
        val current = catalog.zones.current(ctx)
        if (current != null) return current.name
        val drawingId = ctx.drawingId ?: return "—"
        if (layout == null) return "—"
        val zones = catalog.zones.forDrawing(drawingId)
        for (zone in zones) {
            val start = resolveAnchorMm(zone.start, layout) ?: continue
            val end = resolveAnchorMm(zone.end, layout) ?: start
            val lo = minOf(start, end)
            val hi = maxOf(start, end)
            if (routeMm in lo..hi) return zone.name
        }
        return "—"
    }

    private fun resolveAnchorMm(
        anchor: com.example.cnv.factory.model.RouteAnchor,
        layout: HeatMapRouteLayout.LayoutResult,
    ): Float? {
        val segmentId = anchor.segmentId ?: return null
        val progress = anchor.progress ?: 0f
        return HeatMapRouteLayout.absoluteRouteMm(layout, segmentId, progress)
    }

    private fun ensureDashboardLayout(): HeatMapRouteLayout.LayoutResult? {
        dashboardLayout?.let { return it }
        val route = routeRepository.current() ?: return null
        val built = HeatMapRouteLayout.build(route)
        dashboardLayout = built
        return built
    }

    private fun resetLiveAggregates() {
        liveMaxShock = 0f
        liveShockSum = 0f
        liveShockSamples = 0
        liveShockCount = 0
        liveDistanceMm = 0f
        lastFusionTimestampNs = 0L
    }

    private fun startSensors() {
        if (sensorsRunning) return
        fusionEngine.start()
        speedValidatorEngine.start()
        mapMatchingEngine.start()
        imuManager.start()
        routeDebugController.start()
        ensureDashboardLayout()
        sensorsRunning = true
    }

    private fun stopSensors() {
        if (!sensorsRunning) return
        routeDebugController.stop()
        imuManager.stop()
        mapMatchingEngine.stop()
        speedValidatorEngine.stop()
        fusionEngine.stop()
        sensorsRunning = false
        dashboardLayout = null
        resetLiveAggregates()
    }
}
