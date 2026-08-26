package com.example.cnv.ui.legacy.feature

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.example.cnv.R
import com.example.cnv.cad.CADController
import com.example.cnv.cad.CADView
import com.example.cnv.camera.CameraManager
import com.example.cnv.config.CalibrationManager
import com.example.cnv.core.config.IMUConfig
import com.example.cnv.debug.DwgDebugHud
import com.example.cnv.debug.FusionDebugHud
import com.example.cnv.debug.ImuDebugHud
import com.example.cnv.debug.InspectionDebugHud
import com.example.cnv.debug.MapDebugHud
import com.example.cnv.debug.PipelinePerfDebugHud
import com.example.cnv.debug.RouteDebugController
import com.example.cnv.debug.RouteDebugView
import com.example.cnv.debug.RouteGenDebugHud
import com.example.cnv.dwg.DWGImporter
import com.example.cnv.dwg.StubDWGReader
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.fusion.FusionEngine
import com.example.cnv.heatmap.HeatMapController
import com.example.cnv.heatmap.HeatMapOverlay
import com.example.cnv.imu.IMUManager
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.inspection.InspectionResult
import com.example.cnv.inspection.InspectionState
import com.example.cnv.inspection.RouteQualityScore
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.map.RouteRepository
import com.example.cnv.opencv.OpenCVManager
import com.example.cnv.route.RouteGenerator
import com.example.cnv.route.ValidationSeverity

/**
 * Legacy feature wiring (isolated — not hosted by rebuild MainActivity).
 * Owns engines; binds UI per legacy Screen. Does not change domain algorithms.
 */
class FeatureRuntime(
    private val activity: AppCompatActivity,
) {

    private val catalog = FactoryCatalog.get()

    private val openCvManager: OpenCVManager
    private val cameraManager: CameraManager
    private val imuManager: IMUManager = IMUManager(activity)
    private val fusionEngine: FusionEngine
    private val routeRepository: RouteRepository
    private val routeGenerator: RouteGenerator
    private val dwgImporter: DWGImporter
    private val mapMatchingEngine: MapMatchingEngine
    private val inspectionManager: InspectionManager
    private val routeDebugController: RouteDebugController

    private var cadController: CADController? = null
    private var heatMapController: HeatMapController? = null

    private var imuDebugHud: ImuDebugHud? = null
    private var fusionDebugHud: FusionDebugHud? = null
    private var mapDebugHud: MapDebugHud? = null
    private var dwgDebugHud: DwgDebugHud? = null
    private var routeGenDebugHud: RouteGenDebugHud? = null
    private var inspectionDebugHud: InspectionDebugHud? = null
    private var pipelinePerfDebugHud: PipelinePerfDebugHud? = null

    private var inspectionHudRunning = false
    private var heatMapRunning = false
    private var developerHudRunning = false
    private var coreSensorsRunning = false
    private var cameraRunning = false

    private var inspectionStatusViews: InspectionStatusViews? = null
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusRunnable = object : Runnable {
        override fun run() {
            refreshInspectionStatus()
            statusHandler.postDelayed(this, 250L)
        }
    }

    data class InspectionStatusViews(
        val tracking: TextView,
        val distance: TextView,
        val shock: TextView,
        val elapsed: TextView,
    )

    init {
        // Programmatic bootstrap surfaces (legacy isolation — not in NavHost layout).
        val bootstrapPreview = PreviewView(activity).apply { visibility = View.GONE }
        val bootstrapGray = ImageView(activity).apply { visibility = View.GONE }
        openCvManager = OpenCVManager(activity, bootstrapGray)
        cameraManager = CameraManager(activity, bootstrapPreview)

        fusionEngine = FusionEngine(
            initialCalibrated = true,
        )

        routeRepository = catalog.routes.underlying()
        routeGenerator = RouteGenerator(routeRepository = routeRepository)
        dwgImporter = DWGImporter(reader = StubDWGReader())
        // Route must come from Drawing Commissioning — never auto-generate sample routes.

        mapMatchingEngine = MapMatchingEngine(routeRepository = routeRepository)
        inspectionManager = InspectionManager(repository = catalog.inspections.underlying())

        // Route debug controller needs views; use bootstrap no-op containers created lazily.
        val stubRouteView = RouteDebugView(activity)
        stubRouteView.visibility = View.GONE
        routeDebugController = RouteDebugController(
            routeRepository = routeRepository,
            routeDebugView = stubRouteView,
            statsTextView = TextView(activity),
            issuesTextView = TextView(activity),
            mapMatchingEngine = mapMatchingEngine,
            mapperProvider = { routeGenerator.latestResult()?.mapper },
        )
    }

    fun inspectionManager(): InspectionManager = inspectionManager

    fun heatMapController(): HeatMapController? = heatMapController

    fun cadController(): CADController? = cadController

    fun latestInspectionResult(): InspectionResult? = inspectionManager.repository().latest()

    fun startCoreSensors() {
        if (coreSensorsRunning) return
        fusionEngine.start()
        mapMatchingEngine.start()
        imuManager.start()
        routeDebugController.start()
        coreSensorsRunning = true
    }

    fun stopCoreSensorsIfIdle() {
        if (inspectionHudRunning || heatMapRunning || developerHudRunning || cameraRunning) return
        if (!coreSensorsRunning) return
        routeDebugController.stop()
        imuManager.stop()
        mapMatchingEngine.stop()
        fusionEngine.stop()
        coreSensorsRunning = false
    }

    fun attachInspection(preview: PreviewView, status: InspectionStatusViews) {
        cameraManager.attachPreviewView(preview)
        inspectionStatusViews = status
        startCoreSensors()
        if (!cameraRunning) {
            val analyzer = openCvManager.start()
            cameraManager.start(analyzer)
            cameraRunning = true
        }
        bindProductionReliability(camera = true, sensor = true)
        statusHandler.removeCallbacks(statusRunnable)
        statusHandler.post(statusRunnable)
        inspectionHudRunning = true
    }

    fun detachInspection() {
        statusHandler.removeCallbacks(statusRunnable)
        inspectionStatusViews = null
        inspectionHudRunning = false
        if (cameraRunning && !developerHudRunning) {
            cameraManager.stop()
            openCvManager.release()
            cameraRunning = false
            unbindProductionReliability(camera = true)
        }
        stopCoreSensorsIfIdle()
    }

    fun startInspectionSession(): Boolean {
        val route = routeRepository.current() ?: return false
        val calibration = CalibrationManager.getInstance(activity)
        val calData = calibration.getCalibrationData()
        val quality = RouteQualityScore.from(routeDebugController.latestValidation())
        val versionName = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0" }
        inspectionManager.start(
            InspectionManager.StartRequest(
                route = route,
                calibrationVersion = calData?.version ?: 0,
                calibrationValue = calibration.getMmPerPixel(),
                appVersion = versionName,
                deviceInformation = "${Build.MANUFACTURER} ${Build.MODEL}",
                samplingRateHz = 1_000_000f / IMUConfig.DEFAULT_SAMPLING_PERIOD_US,
                routeQualityScore = quality,
                mapper = catalog.routes.underlying().currentMapper(),
            ),
        )
        return true
    }

    fun stopInspectionSession(): InspectionResult? {
        val result = inspectionManager.stop()
        val drawingId = CurrentContext.get().drawingId
        if (result != null && drawingId != null) {
            catalog.inspections.index(drawingId, result.sessionId)
        }
        return result
    }

    fun attachHeatMap(root: View) {
        val cadView = root.findViewById<CADView>(R.id.cad_view)
        val overlay = root.findViewById<HeatMapOverlay>(R.id.heatmap_overlay)
        startCoreSensors()
        cadController = CADController(
            routeRepository = routeRepository,
            cadView = cadView,
            mapperProvider = { routeGenerator.latestResult()?.mapper },
            debugHud = root.findViewById(R.id.cad_debug_hud),
            validationErrorProvider = {
                val issues = routeDebugController.latestValidation()?.issues.orEmpty()
                val firstError = issues.firstOrNull { it.severity == ValidationSeverity.ERROR }
                firstError?.let { "Err: ${it.message}" }
            },
            inspectionStateProvider = {
                "Inspection: ${inspectionManager.state().name}"
            },
            errorSegmentIdsProvider = {
                routeDebugController.latestValidation()?.issues.orEmpty()
                    .filter { it.severity == ValidationSeverity.ERROR && it.segmentId != null }
                    .mapNotNull { it.segmentId }
                    .toSet()
            },
            selectionInfoView = root.findViewById(R.id.cad_selection_info),
        )
        heatMapController = HeatMapController(
            inspectionManager = inspectionManager,
            overlay = overlay,
            cadView = cadView,
            mapperProvider = { routeGenerator.latestResult()?.mapper },
            debugHud = root.findViewById(R.id.heatmap_debug_hud),
        )
        bindCadButtons(root)
        bindHeatMapControls(root)
        cadController?.start()
        heatMapController?.start()
        heatMapRunning = true
    }

    fun detachHeatMap() {
        heatMapController?.stop()
        cadController?.stop()
        heatMapController = null
        cadController = null
        heatMapRunning = false
        stopCoreSensorsIfIdle()
    }

    fun attachDeveloper(root: View) {
        startCoreSensors()
        val gray = root.findViewById<ImageView>(R.id.opencv_gray_view)
        openCvManager.attachGrayImageView(gray)
        if (!cameraRunning) {
            // Developer gray preview needs analyzer; camera optional for gray if already stopped.
            // Keep sensors only; gray may wait until inspection ran once.
        }
        imuDebugHud = ImuDebugHud(root.findViewById(R.id.imu_debug_hud), imuManager.repository)
        fusionDebugHud = FusionDebugHud(root.findViewById(R.id.fusion_debug_hud), fusionEngine.repository)
        mapDebugHud = MapDebugHud(root.findViewById(R.id.map_debug_hud), mapMatchingEngine)
        dwgDebugHud = DwgDebugHud(root.findViewById(R.id.dwg_debug_hud), dwgImporter)
        routeGenDebugHud = RouteGenDebugHud(root.findViewById(R.id.route_gen_debug_hud), routeGenerator)
        inspectionDebugHud = InspectionDebugHud(
            root.findViewById(R.id.inspection_debug_hud),
            inspectionManager,
        )
        pipelinePerfDebugHud = PipelinePerfDebugHud(root.findViewById(R.id.pipeline_perf_debug_hud))

        val routeDebugView = root.findViewById<RouteDebugView>(R.id.route_debug_view)
        routeDebugView.visibility = View.VISIBLE
        root.findViewById<Button>(R.id.button_route_zoom_in).setOnClickListener { routeDebugView.zoomIn() }
        root.findViewById<Button>(R.id.button_route_zoom_out).setOnClickListener { routeDebugView.zoomOut() }

        imuDebugHud?.start()
        fusionDebugHud?.start()
        mapDebugHud?.start()
        dwgDebugHud?.start()
        routeGenDebugHud?.start()
        inspectionDebugHud?.start()
        pipelinePerfDebugHud?.start()
        developerHudRunning = true
    }

    fun detachDeveloper() {
        pipelinePerfDebugHud?.stop()
        inspectionDebugHud?.stop()
        routeGenDebugHud?.stop()
        dwgDebugHud?.stop()
        mapDebugHud?.stop()
        fusionDebugHud?.stop()
        imuDebugHud?.stop()
        pipelinePerfDebugHud = null
        inspectionDebugHud = null
        routeGenDebugHud = null
        dwgDebugHud = null
        mapDebugHud = null
        fusionDebugHud = null
        imuDebugHud = null
        developerHudRunning = false
        stopCoreSensorsIfIdle()
    }

    fun releaseAll() {
        detachInspection()
        detachHeatMap()
        detachDeveloper()
        if (cameraRunning) {
            cameraManager.stop()
            openCvManager.release()
            cameraRunning = false
        }
        if (coreSensorsRunning) {
            routeDebugController.stop()
            imuManager.stop()
            mapMatchingEngine.stop()
            fusionEngine.stop()
            coreSensorsRunning = false
        }
    }

    private fun refreshInspectionStatus() {
        val views = inspectionStatusViews ?: return
        val fusion = fusionEngine.repository.latest()
        val position = mapMatchingEngine.latestPosition()
        val session = inspectionManager.currentSession()
        val state = inspectionManager.state()

        views.tracking.text = activity.getString(
            R.string.inspection_tracking_value,
            when {
                state == InspectionState.RUNNING && position != null -> "LOCKED"
                state == InspectionState.RUNNING -> "SEARCHING"
                else -> state.name
            },
        )
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
        views.distance.text = activity.getString(R.string.inspection_distance_value, distance)
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
        views.shock.text = activity.getString(R.string.inspection_shock_value, shocks)
        val elapsedSec = if (session != null && state == InspectionState.RUNNING) {
            session.elapsedMs() / 1000.0
        } else {
            0.0
        }
        views.elapsed.text = activity.getString(R.string.inspection_elapsed_value, elapsedSec)
    }

    private fun bindCadButtons(root: View) {
        val cad = cadController ?: return
        root.findViewById<Button>(R.id.button_cad_zoom_in)?.setOnClickListener { cad.zoomIn() }
        root.findViewById<Button>(R.id.button_cad_zoom_out)?.setOnClickListener { cad.zoomOut() }
        root.findViewById<Button>(R.id.button_cad_fit)?.setOnClickListener { cad.fitToRoute() }
        root.findViewById<Button>(R.id.button_cad_reset)?.setOnClickListener { cad.resetView() }
        root.findViewById<Button>(R.id.button_heatmap_zoom_in)?.setOnClickListener { cad.zoomIn() }
        root.findViewById<Button>(R.id.button_heatmap_zoom_out)?.setOnClickListener { cad.zoomOut() }
        root.findViewById<Button>(R.id.button_heatmap_fit)?.setOnClickListener { cad.fitToRoute() }
        root.findViewById<Button>(R.id.button_heatmap_reset)?.setOnClickListener { cad.resetView() }
        root.findViewById<Button>(R.id.button_heatmap_search)?.setOnClickListener {
            val input = root.findViewById<EditText>(R.id.cad_search_input) ?: return@setOnClickListener
            if (input.visibility != View.VISIBLE) {
                input.visibility = View.VISIBLE
            } else {
                cad.search(input.text?.toString().orEmpty())
            }
        }
        root.findViewById<Button>(R.id.button_cad_search)?.setOnClickListener {
            val query = root.findViewById<EditText>(R.id.cad_search_input)?.text?.toString().orEmpty()
            cad.search(query)
        }
    }

    private fun bindHeatMapControls(root: View) {
        val controller = heatMapController ?: return
        root.findViewById<Button>(R.id.button_heat_toggle)?.setOnClickListener {
            controller.toggleShockLayer()
        }
        val startSeek = root.findViewById<SeekBar>(R.id.heatmap_timeline_start) ?: return
        val endSeek = root.findViewById<SeekBar>(R.id.heatmap_timeline_end) ?: return
        val timelineLabel = root.findViewById<TextView>(R.id.heatmap_timeline_label)

        fun refreshTimelineLabel() {
            val tc = controller.timelineController()
            val (startText, endText) = tc.formatStartEnd()
            timelineLabel?.text = "%s | %s | %s".format(startText, endText, tc.formatCurrentRange())
        }

        fun applyTimelineFromSeekBars() {
            var start01 = startSeek.progress / 1000f
            var end01 = endSeek.progress / 1000f
            if (end01 < start01) {
                endSeek.progress = startSeek.progress
                end01 = start01
            }
            controller.timelineController().setRangeProgress(start01, end01)
            refreshTimelineLabel()
            controller.notifyFilterChanged()
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) applyTimelineFromSeekBars()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = applyTimelineFromSeekBars()
        }
        startSeek.setOnSeekBarChangeListener(seekListener)
        endSeek.setOnSeekBarChangeListener(seekListener)

        root.findViewById<Button>(R.id.button_heatmap_timeline_reset)?.setOnClickListener {
            controller.timelineController().reset()
            startSeek.progress = 0
            endSeek.progress = 1000
            refreshTimelineLabel()
            controller.notifyFilterChanged()
        }

        fun applyFilterFields() {
            val fc = controller.filterController()
            fc.setShockRange(
                root.findViewById<EditText>(R.id.heatmap_filter_shock_min)?.text?.toString()?.toFloatOrNull() ?: 0f,
                root.findViewById<EditText>(R.id.heatmap_filter_shock_max)?.text?.toString()?.toFloatOrNull()
                    ?: Float.MAX_VALUE,
            )
            fc.setConfidenceRange(
                root.findViewById<EditText>(R.id.heatmap_filter_conf_min)?.text?.toString()?.toFloatOrNull() ?: 0f,
                root.findViewById<EditText>(R.id.heatmap_filter_conf_max)?.text?.toString()?.toFloatOrNull() ?: 1f,
            )
            fc.setSegmentId(root.findViewById<EditText>(R.id.heatmap_filter_segment)?.text?.toString())
            fc.setNodeId(root.findViewById<EditText>(R.id.heatmap_filter_node)?.text?.toString())
            fc.setSessionId(root.findViewById<EditText>(R.id.heatmap_filter_session)?.text?.toString())
            controller.notifyFilterChanged()
        }
        root.findViewById<Button>(R.id.button_heatmap_filter_apply)?.setOnClickListener { applyFilterFields() }
        root.findViewById<Button>(R.id.button_heatmap_filter_reset)?.setOnClickListener {
            controller.filterController().reset()
            controller.notifyFilterChanged()
        }
        root.findViewById<Button>(R.id.button_heatmap_timeline)?.setOnClickListener {
            root.findViewById<View>(R.id.heatmap_filter_panel)?.visibility = View.VISIBLE
        }
        root.findViewById<Button>(R.id.button_heatmap_filter)?.setOnClickListener {
            root.findViewById<View>(R.id.heatmap_filter_panel)?.visibility = View.VISIBLE
        }
        refreshTimelineLabel()
    }

    private fun bindProductionReliability(camera: Boolean, sensor: Boolean) {
        val watchdog = com.example.cnv.production.ProductionWatchdog.shared()
        com.example.cnv.production.RecoveryCoordinator.registerCameraReinit {
            if (cameraRunning) {
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
        if (camera) watchdog.setCameraExpected(true)
        if (sensor) watchdog.setSensorExpected(true)
        watchdog.start()
    }

    private fun unbindProductionReliability(camera: Boolean) {
        val watchdog = com.example.cnv.production.ProductionWatchdog.shared()
        if (camera) {
            watchdog.setCameraExpected(false)
            com.example.cnv.production.RecoveryCoordinator.registerCameraReinit(null)
        }
        if (!inspectionHudRunning && !developerHudRunning && !cameraRunning) {
            watchdog.setSensorExpected(false)
            watchdog.setListener(null)
            watchdog.stop()
        }
    }
}
