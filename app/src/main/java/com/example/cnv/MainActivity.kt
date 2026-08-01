package com.example.cnv

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cnv.camera.CameraManager
import com.example.cnv.config.CalibrationManager
import com.example.cnv.debug.DwgDebugHud
import com.example.cnv.debug.FusionDebugHud
import com.example.cnv.debug.ImuDebugHud
import com.example.cnv.debug.MapDebugHud
import com.example.cnv.debug.RouteGenDebugHud
import com.example.cnv.dwg.DWGImporter
import com.example.cnv.dwg.StubDWGReader
import com.example.cnv.fusion.FusionEngine
import com.example.cnv.imu.IMUManager
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.map.RouteRepository
import com.example.cnv.opencv.OpenCVManager
import com.example.cnv.route.RouteGenerator
import com.example.cnv.ui.calibration.CalibrationActivity

class MainActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var openCvManager: OpenCVManager
    private lateinit var imuManager: IMUManager
    private lateinit var fusionEngine: FusionEngine
    private lateinit var mapMatchingEngine: MapMatchingEngine
    private lateinit var dwgImporter: DWGImporter
    private lateinit var routeGenerator: RouteGenerator
    private lateinit var imuDebugHud: ImuDebugHud
    private lateinit var fusionDebugHud: FusionDebugHud
    private lateinit var mapDebugHud: MapDebugHud
    private lateinit var dwgDebugHud: DwgDebugHud
    private lateinit var routeGenDebugHud: RouteGenDebugHud

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val previewView = findViewById<PreviewView>(R.id.preview_view)
        val grayImageView = findViewById<ImageView>(R.id.opencv_gray_view)

        openCvManager = OpenCVManager(this, grayImageView)
        val analyzer = openCvManager.start()

        cameraManager = CameraManager(this, previewView)
        cameraManager.start(analyzer)

        imuManager = IMUManager(this)
        imuDebugHud = ImuDebugHud(
            textView = findViewById(R.id.imu_debug_hud),
            repository = imuManager.repository,
        )

        fusionEngine = FusionEngine(
            initialCalibrated = CalibrationManager.getInstance(this).isCalibrated(),
        )
        fusionDebugHud = FusionDebugHud(
            textView = findViewById(R.id.fusion_debug_hud),
            repository = fusionEngine.repository,
        )

        val routeRepository = RouteRepository()
        routeGenerator = RouteGenerator(routeRepository = routeRepository)

        dwgImporter = DWGImporter(reader = StubDWGReader())
        val dwgResult = dwgImporter.importFrom("stub://demo-conveyor.dwg")
        routeGenerator.generate(candidates = dwgResult.candidates)

        mapMatchingEngine = MapMatchingEngine(routeRepository = routeRepository)
        mapDebugHud = MapDebugHud(
            textView = findViewById(R.id.map_debug_hud),
            mapMatchingEngine = mapMatchingEngine,
        )
        dwgDebugHud = DwgDebugHud(
            textView = findViewById(R.id.dwg_debug_hud),
            importer = dwgImporter,
        )
        routeGenDebugHud = RouteGenDebugHud(
            textView = findViewById(R.id.route_gen_debug_hud),
            routeGenerator = routeGenerator,
        )

        findViewById<Button>(R.id.button_open_calibration).setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        fusionEngine.start()
        mapMatchingEngine.start()
        imuManager.start()
        imuDebugHud.start()
        fusionDebugHud.start()
        mapDebugHud.start()
        dwgDebugHud.start()
        routeGenDebugHud.start()
    }

    override fun onStop() {
        routeGenDebugHud.stop()
        dwgDebugHud.stop()
        mapDebugHud.stop()
        fusionDebugHud.stop()
        imuDebugHud.stop()
        imuManager.stop()
        mapMatchingEngine.stop()
        fusionEngine.stop()
        super.onStop()
    }
}
