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
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.inspection.InspectionState
import com.example.cnv.inspection.RouteQualityScore
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.opencv.OpenCVManager
import com.example.cnv.route.RouteGenerator
import com.example.cnv.speed.SpeedValidatorEngine

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
    }

    fun detach() {
        if (detached) return
        detached = true
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
        if (drawingId != null) {
            val conveyorLive = drawing?.conveyorProfile
            com.example.cnv.inspection.db.InspectionDbGate.execute {
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
                )
                // Same background thread — avoid nested DbGate submit.
                catalog.inspectionProfiles.saveSync(drawingId, profile)
            }
        }
        return true
    }

    fun stopSession(): InspectionResult? {
        val active = inspectionManager.currentSession()
        val events = active?.recorder()?.snapshot().orEmpty()
        val appVersion = active?.freeze?.appVersion.orEmpty()
        val profileSnap = sessionConveyorSnapshot
        sessionConveyorSnapshot = null
        val speedSummary = speedValidatorEngine.endSession()
        val result = inspectionManager.stop()
        val drawingId = CurrentContext.get().drawingId
        if (result != null && drawingId != null) {
            val resolvedAppVersion = appVersion.ifBlank {
                runCatching {
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
                }.getOrNull().orEmpty().ifBlank { "1.0" }
            }
            // Persist + HeatLayer regenerate off main thread (STOP must not block UI).
            val route = routeRepository.current()
            val mapper = routeGenerator.latestResult()?.mapper
            InspectionDbGate.execute {
                catalog.inspections.finishSession(
                    drawingId = drawingId,
                    result = result,
                    events = events,
                    appVersion = resolvedAppVersion,
                    speedValidation = speedSummary,
                    conveyorProfile = profileSnap,
                )
                if (route != null) {
                    catalog.heatMaps.regenerateHeatLayer(
                        drawingId = drawingId,
                        inspectionRepository = catalog.inspections.underlying(),
                        route = route,
                        mapper = mapper,
                    )
                }
            }
        }
        return result
    }

    fun readStatus(): InspectionUiStatus {
        val fusion = fusionEngine.repository.latest()
        val position = mapMatchingEngine.latestPosition()
        val session = inspectionManager.currentSession()
        val state = inspectionManager.state()
        val tracking = when {
            state == InspectionState.RUNNING && position != null -> "LOCKED"
            state == InspectionState.RUNNING -> "SEARCHING"
            else -> state.name
        }
        val distance = when {
            session != null && state == InspectionState.RUNNING -> {
                session.recorder().computeStatistics(
                    freeze = session.freeze,
                    startTimeMs = session.startTimeMs,
                    endTimeMs = System.currentTimeMillis(),
                ).totalDistanceMm
            }
            fusion != null -> fusion.distance
            else -> 0f
        }
        val shocks = when {
            session != null && state == InspectionState.RUNNING -> {
                session.recorder().computeStatistics(
                    freeze = session.freeze,
                    startTimeMs = session.startTimeMs,
                    endTimeMs = System.currentTimeMillis(),
                ).shockCount
            }
            else -> 0
        }
        val elapsedSec = if (session != null && state == InspectionState.RUNNING) {
            session.elapsedMs() / 1000.0
        } else {
            0.0
        }
        val sample = speedValidatorEngine.latest()
        return InspectionUiStatus(
            trackingLabel = tracking,
            distanceMm = distance,
            shockCount = shocks,
            elapsedSec = elapsedSec,
            sessionState = state.name,
            running = state == InspectionState.RUNNING,
            speedMismatchWarning = speedValidatorEngine.mismatchWarning(),
            speedValidationConfidence = sample?.confidence,
            validatedFusionConfidence = sample?.validatedFusionConfidence,
        )
    }

    private fun startSensors() {
        if (sensorsRunning) return
        fusionEngine.start()
        speedValidatorEngine.start()
        mapMatchingEngine.start()
        imuManager.start()
        routeDebugController.start()
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
    }
}
