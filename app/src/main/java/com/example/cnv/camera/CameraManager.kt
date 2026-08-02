package com.example.cnv.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider

class CameraManager(
    private val activity: AppCompatActivity,
    private var previewView: PreviewView,
) {

    private val viewModel: CameraViewModel =
        ViewModelProvider(activity)[CameraViewModel::class.java]

    private var analyzer: ImageAnalysis.Analyzer? = null

    /**
     * Keyed registry registration (no LifecycleOwner) — safe when Activity is already RESUMED.
     * [ComponentActivity.registerForActivityResult] requires registration before STARTED and
     * crashes when InspectionPipeline creates CameraManager from a resumed Fragment.
     */
    private val requestCameraPermission: ActivityResultLauncher<String> =
        activity.activityResultRegistry.register(
            "cnv_camera_permission_${System.identityHashCode(this)}",
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                startPreview()
            }
        }

    /** Rebind preview surface for Inspection screen migration (wiring only). */
    fun attachPreviewView(view: PreviewView) {
        previewView = view
    }

    fun start(analyzer: ImageAnalysis.Analyzer? = null) {
        this.analyzer = analyzer
        if (hasCameraPermission()) {
            startPreview()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    /** Unbinds CameraX use-cases (stops analysis in background). */
    fun stop() {
        viewModel.unbind()
    }

    /** Recovery: rebind CameraX without recreating managers. */
    fun reinitialize() {
        if (hasCameraPermission()) {
            viewModel.rebindIfPossible(activity)
        } else {
            startPreview()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startPreview() {
        viewModel.bindPreview(activity, previewView, activity, analyzer)
    }
}
