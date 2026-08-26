package com.example.cnv.ui.legacy

import android.content.Intent
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
import com.example.cnv.debug.DwgDebugHud
import com.example.cnv.debug.FusionDebugHud
import com.example.cnv.debug.ImuDebugHud
import com.example.cnv.debug.MapDebugHud
import com.example.cnv.debug.PipelinePerfDebugHud
import com.example.cnv.debug.RouteDebugController
import com.example.cnv.debug.RouteDebugView
import com.example.cnv.debug.RouteGenDebugHud
import com.example.cnv.dwg.DWGImporter
import com.example.cnv.dwg.StubDWGReader
import com.example.cnv.fusion.FusionEngine
import com.example.cnv.heatmap.HeatMapController
import com.example.cnv.heatmap.HeatMapOverlay
import com.example.cnv.imu.IMUManager
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.inspection.InspectionState
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.map.RouteRepository
import com.example.cnv.opencv.OpenCVManager
import com.example.cnv.route.RouteGenerator
import com.example.cnv.route.ValidationSeverity
import com.example.cnv.ui.calibration.CalibrationActivity

/**
 * Legacy monolith wiring for [R.layout.legacy_activity_main].
 * Isolated in UI Rebuild Phase 1 — MainActivity does not call this.
 */
class LegacyMainCompositionRoot(
    private val activity: AppCompatActivity,
) {

    private lateinit var cameraManager: CameraManager
    private lateinit var openCvManager: OpenCVManager
    private lateinit var imuManager: IMUManager
    private lateinit var fusionEngine: FusionEngine
    private lateinit var mapMatchingEngine: MapMatchingEngine
    private lateinit var dwgImporter: DWGImporter
    private lateinit var routeGenerator: RouteGenerator
    private lateinit var routeDebugController: RouteDebugController
    private lateinit var inspectionManager: InspectionManager
    private lateinit var cadController: CADController
    private lateinit var imuDebugHud: ImuDebugHud
    private lateinit var fusionDebugHud: FusionDebugHud
    private lateinit var mapDebugHud: MapDebugHud
    private lateinit var dwgDebugHud: DwgDebugHud
    private lateinit var routeGenDebugHud: RouteGenDebugHud
    private lateinit var pipelinePerfDebugHud: PipelinePerfDebugHud
    private lateinit var heatMapController: HeatMapController
    private lateinit var routeRepository: RouteRepository

    fun bind() {
        // PreviewView removed from legacy layout — Inspection Camera lives on InspectionScreen only.
        val previewView = PreviewView(activity).apply { visibility = View.GONE }
        val grayImageView = activity.findViewById<ImageView>(R.id.opencv_gray_view)

        openCvManager = OpenCVManager(activity, grayImageView)
        cameraManager = CameraManager(activity, previewView)

        bindImuAndFusion()
        bindRoutePipeline()
        bindInspectionManagerOnly()
        bindCadViewer()
        bindHeatMap()

        pipelinePerfDebugHud = PipelinePerfDebugHud(
            textView = activity.findViewById(R.id.pipeline_perf_debug_hud),
        )
    }

    fun onStart() {
        val analyzer = openCvManager.start()
        cameraManager.start(analyzer)

        fusionEngine.start()
        mapMatchingEngine.start()
        imuManager.start()
        imuDebugHud.start()
        fusionDebugHud.start()
        mapDebugHud.start()
        dwgDebugHud.start()
        routeGenDebugHud.start()
        routeDebugController.start()
        pipelinePerfDebugHud.start()
        cadController.start()
        heatMapController.start()
    }

    fun onStop() {
        if (inspectionManager.state() == InspectionState.RUNNING) {
            inspectionManager.stop()
        }
        heatMapController.stop()
        cadController.stop()
        pipelinePerfDebugHud.stop()
        routeDebugController.stop()
        routeGenDebugHud.stop()
        dwgDebugHud.stop()
        mapDebugHud.stop()
        fusionDebugHud.stop()
        imuDebugHud.stop()
        imuManager.stop()
        mapMatchingEngine.stop()
        fusionEngine.stop()
        // Camera -Analyzer -OpenCV (STEP 10-5 lifecycle order)
        cameraManager.stop()
        openCvManager.release()
    }

    private fun bindImuAndFusion() {
        imuManager = IMUManager(activity)
        imuDebugHud = ImuDebugHud(
            textView = activity.findViewById(R.id.imu_debug_hud),
            repository = imuManager.repository,
        )

        fusionEngine = FusionEngine(
            initialCalibrated = true,
        )
        fusionDebugHud = FusionDebugHud(
            textView = activity.findViewById(R.id.fusion_debug_hud),
            repository = fusionEngine.repository,
        )
    }

    private fun bindRoutePipeline() {
        routeRepository = RouteRepository()
        routeGenerator = RouteGenerator(routeRepository = routeRepository)

        dwgImporter = DWGImporter(reader = StubDWGReader())
        // Route comes from Drawing Commissioning — do not auto-generate here.

        mapMatchingEngine = MapMatchingEngine(routeRepository = routeRepository)
        mapDebugHud = MapDebugHud(
            textView = activity.findViewById(R.id.map_debug_hud),
            mapMatchingEngine = mapMatchingEngine,
        )
        dwgDebugHud = DwgDebugHud(
            textView = activity.findViewById(R.id.dwg_debug_hud),
            importer = dwgImporter,
        )
        routeGenDebugHud = RouteGenDebugHud(
            textView = activity.findViewById(R.id.route_gen_debug_hud),
            routeGenerator = routeGenerator,
        )

        val routeDebugView = activity.findViewById<RouteDebugView>(R.id.route_debug_view)
        routeDebugController = RouteDebugController(
            routeRepository = routeRepository,
            routeDebugView = routeDebugView,
            statsTextView = activity.findViewById(R.id.route_validation_hud),
            issuesTextView = activity.findViewById(R.id.route_issues_hud),
            mapMatchingEngine = mapMatchingEngine,
            mapperProvider = { routeGenerator.latestResult()?.mapper },
        )
        activity.findViewById<Button>(R.id.button_route_zoom_in)
            .setOnClickListener { routeDebugView.zoomIn() }
        activity.findViewById<Button>(R.id.button_route_zoom_out)
            .setOnClickListener { routeDebugView.zoomOut() }
    }

    private fun bindCadViewer() {
        val cadView = activity.findViewById<CADView>(R.id.cad_view)
        cadController = CADController(
            routeRepository = routeRepository,
            cadView = cadView,
            mapperProvider = { routeGenerator.latestResult()?.mapper },
            debugHud = activity.findViewById(R.id.cad_debug_hud),
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
            selectionInfoView = activity.findViewById(R.id.cad_selection_info),
        )
        activity.findViewById<Button>(R.id.button_cad_zoom_in)
            .setOnClickListener { cadController.zoomIn() }
        activity.findViewById<Button>(R.id.button_cad_zoom_out)
            .setOnClickListener { cadController.zoomOut() }
        activity.findViewById<Button>(R.id.button_cad_fit)
            .setOnClickListener { cadController.fitToRoute() }
        activity.findViewById<Button>(R.id.button_cad_reset)
            .setOnClickListener { cadController.resetView() }
        activity.findViewById<Button>(R.id.button_cad_theme)
            .setOnClickListener { cadController.toggleTheme() }
        activity.findViewById<Button>(R.id.button_cad_goto_pos)
            .setOnClickListener { cadController.goToCurrentPosition() }
        activity.findViewById<Button>(R.id.button_cad_goto_start)
            .setOnClickListener { cadController.goToStart() }
        activity.findViewById<Button>(R.id.button_cad_goto_end)
            .setOnClickListener { cadController.goToEnd() }
        activity.findViewById<Button>(R.id.button_cad_center)
            .setOnClickListener { cadController.centerCurrentPosition() }
        activity.findViewById<Button>(R.id.button_cad_search)
            .setOnClickListener {
                val query = activity.findViewById<android.widget.EditText>(R.id.cad_search_input).text?.toString().orEmpty()
                cadController.search(query)
            }
        activity.findViewById<Button>(R.id.button_cad_layer_grid)
            .setOnClickListener { cadController.toggleLayer(com.example.cnv.cad.CADLayer.GRID) }
        activity.findViewById<Button>(R.id.button_cad_layer_node)
            .setOnClickListener { cadController.toggleLayer(com.example.cnv.cad.CADLayer.NODE) }
        activity.findViewById<Button>(R.id.button_cad_layer_branch)
            .setOnClickListener { cadController.toggleLayer(com.example.cnv.cad.CADLayer.BRANCH) }
    }

    private fun bindHeatMap() {
        val cadView = activity.findViewById<CADView>(R.id.cad_view)
        val overlay = activity.findViewById<HeatMapOverlay>(R.id.heatmap_overlay)
        heatMapController = HeatMapController(
            inspectionManager = inspectionManager,
            overlay = overlay,
            cadView = cadView,
            mapperProvider = { routeGenerator.latestResult()?.mapper },
            debugHud = activity.findViewById(R.id.heatmap_debug_hud),
        )
        activity.findViewById<Button>(R.id.button_heat_toggle)
            .setOnClickListener { heatMapController.toggleShockLayer() }
        bindHeatMapTimelineAndFilterUi()
    }

    private fun bindHeatMapTimelineAndFilterUi() {
        val timelineLabel = activity.findViewById<TextView>(R.id.heatmap_timeline_label)
        val startSeek = activity.findViewById<SeekBar>(R.id.heatmap_timeline_start)
        val endSeek = activity.findViewById<SeekBar>(R.id.heatmap_timeline_end)
        val shockMin = activity.findViewById<EditText>(R.id.heatmap_filter_shock_min)
        val shockMax = activity.findViewById<EditText>(R.id.heatmap_filter_shock_max)
        val confMin = activity.findViewById<EditText>(R.id.heatmap_filter_conf_min)
        val confMax = activity.findViewById<EditText>(R.id.heatmap_filter_conf_max)
        val segmentInput = activity.findViewById<EditText>(R.id.heatmap_filter_segment)
        val nodeInput = activity.findViewById<EditText>(R.id.heatmap_filter_node)
        val sessionInput = activity.findViewById<EditText>(R.id.heatmap_filter_session)

        fun refreshTimelineLabel() {
            val tc = heatMapController.timelineController()
            val (startText, endText) = tc.formatStartEnd()
            timelineLabel.text = "%s | %s | %s".format(
                startText,
                endText,
                tc.formatCurrentRange(),
            )
        }

        fun applyTimelineFromSeekBars() {
            var start01 = startSeek.progress / 1000f
            var end01 = endSeek.progress / 1000f
            if (end01 < start01) {
                endSeek.progress = startSeek.progress
                end01 = start01
            }
            heatMapController.timelineController().setRangeProgress(start01, end01)
            refreshTimelineLabel()
            heatMapController.notifyFilterChanged()
        }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                applyTimelineFromSeekBars()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                applyTimelineFromSeekBars()
            }
        }
        startSeek.setOnSeekBarChangeListener(seekListener)
        endSeek.setOnSeekBarChangeListener(seekListener)

        activity.findViewById<Button>(R.id.button_heatmap_timeline_reset).setOnClickListener {
            heatMapController.timelineController().reset()
            startSeek.progress = 0
            endSeek.progress = 1000
            refreshTimelineLabel()
            heatMapController.notifyFilterChanged()
        }

        fun applyFilterFields() {
            val fc = heatMapController.filterController()
            val minShock = shockMin.text?.toString()?.toFloatOrNull() ?: 0f
            val maxShock = shockMax.text?.toString()?.toFloatOrNull() ?: Float.MAX_VALUE
            val minConf = confMin.text?.toString()?.toFloatOrNull() ?: 0f
            val maxConf = confMax.text?.toString()?.toFloatOrNull() ?: 1f
            fc.setShockRange(minShock, maxShock)
            fc.setConfidenceRange(minConf, maxConf)
            fc.setSegmentId(segmentInput.text?.toString())
            fc.setNodeId(nodeInput.text?.toString())
            fc.setSessionId(sessionInput.text?.toString())
            heatMapController.notifyFilterChanged()
        }

        activity.findViewById<Button>(R.id.button_heatmap_filter_apply)
            .setOnClickListener { applyFilterFields() }

        activity.findViewById<Button>(R.id.button_heatmap_filter_reset).setOnClickListener {
            heatMapController.filterController().reset()
            shockMin.text = null
            shockMax.text = null
            confMin.text = null
            confMax.text = null
            segmentInput.text = null
            nodeInput.text = null
            sessionInput.text = null
            heatMapController.notifyFilterChanged()
        }

        refreshTimelineLabel()
    }

    private fun bindInspectionManagerOnly() {
        // Inspection UI removed — manager retained for HeatMap / debug consumers only.
        inspectionManager = InspectionManager()
        activity.findViewById<Button>(R.id.button_open_calibration)?.setOnClickListener {
            activity.startActivity(Intent(activity, CalibrationActivity::class.java))
        }
    }
}
