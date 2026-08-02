package com.example.cnv.ui.calibration

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cnv.R
import com.example.cnv.camera.CameraManager
import com.example.cnv.opencv.OpenCVManager

/**
 * Calibration host — wires Camera Preview + ImageAnalysis (Optical Flow) for scale session.
 * Does not change Camera / OpenCV / Optical Flow algorithms.
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var openCvManager: OpenCVManager
    private var cameraRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_calibration)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.calibration_root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // registerForActivityResult must run while Activity is initializing (same pattern as Inspection).
        val bootstrapPreview = PreviewView(this).apply { visibility = View.GONE }
        val bootstrapGray = ImageView(this).apply { visibility = View.GONE }
        cameraManager = CameraManager(this, bootstrapPreview)
        openCvManager = OpenCVManager(this, bootstrapGray)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.calibration_fragment_container, CalibrationFragment())
                .commit()
        }
    }

    /** Bind Preview + Analyzer to the Calibration [PreviewView]. */
    fun startCameraPipeline(previewView: PreviewView) {
        cameraManager.attachPreviewView(previewView)
        if (cameraRunning) {
            cameraManager.reinitialize()
            return
        }
        val analyzer = openCvManager.start()
        cameraManager.start(analyzer)
        cameraRunning = true
    }

    fun stopCameraPipeline() {
        if (!cameraRunning) return
        cameraManager.stop()
        openCvManager.release()
        cameraRunning = false
    }

    override fun onDestroy() {
        stopCameraPipeline()
        super.onDestroy()
    }
}
