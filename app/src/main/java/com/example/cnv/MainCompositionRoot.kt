package com.example.cnv

import android.content.Intent
import android.os.Build
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
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
import com.example.cnv.fusion.FusionEngine
import com.example.cnv.imu.IMUManager
import com.example.cnv.inspection.InspectionManager
import com.example.cnv.inspection.InspectionState
import com.example.cnv.inspection.RouteQualityScore
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.map.RouteRepository
import com.example.cnv.opencv.OpenCVManager
import com.example.cnv.route.RouteGenerator
import com.example.cnv.route.ValidationSeverity
import com.example.cnv.ui.calibration.CalibrationActivity

/**
 * Composition root for MainActivity. Wires features only — no domain algorithms.
 */
class MainCompositionRoot(
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
    private lateinit var inspectionDebugHud: InspectionDebugHud
    private lateinit var pipelinePerfDebugHud: PipelinePerfDebugHud
    private lateinit var routeRepository: RouteRepository

    fun bind() {
        val previewView = activity.findViewById<PreviewView>(R.id.preview_view)
        val grayImageView = activity.findViewById<ImageView>(R.id.opencv_gray_view)

        openCvManager = OpenCVManager(activity, grayImageView)
        cameraManager = CameraManager(activity, previewView)

        bindImuAndFusion()
        bindRoutePipeline()
        bindInspectionAndUiActions()
        bindCadViewer()

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
        inspectionDebugHud.start()
        pipelinePerfDebugHud.start()
        cadController.start()
    }

    fun onStop() {
        if (inspectionManager.state() == InspectionState.RUNNING) {
            inspectionManager.stop()
        }
        cadController.stop()
        pipelinePerfDebugHud.stop()
        inspectionDebugHud.stop()
        routeDebugController.stop()
        routeGenDebugHud.stop()
        dwgDebugHud.stop()
        mapDebugHud.stop()
        fusionDebugHud.stop()
        imuDebugHud.stop()
        imuManager.stop()
        mapMatchingEngine.stop()
        fusionEngine.stop()
        // Camera → Analyzer → OpenCV (STEP 10-5 lifecycle order)
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
            initialCalibrated = CalibrationManager.getInstance(activity).isCalibrated(),
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
        val dwgResult = dwgImporter.importFrom("stub://demo-conveyor.dwg")
        routeGenerator.generate(candidates = dwgResult.candidates)

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
    }

    private fun bindInspectionAndUiActions() {
        inspectionManager = InspectionManager()
        inspectionDebugHud = InspectionDebugHud(
            textView = activity.findViewById(R.id.inspection_debug_hud),
            inspectionManager = inspectionManager,
        )
        activity.findViewById<Button>(R.id.button_inspection_start)
            .setOnClickListener { startInspectionSession() }
        activity.findViewById<Button>(R.id.button_inspection_stop)
            .setOnClickListener { inspectionManager.stop() }
        activity.findViewById<Button>(R.id.button_open_calibration)
            .setOnClickListener {
                activity.startActivity(Intent(activity, CalibrationActivity::class.java))
            }
    }

    private fun startInspectionSession() {
        val route = routeRepository.current() ?: return
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
            ),
        )
    }
}
