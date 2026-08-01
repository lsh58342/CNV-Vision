package com.example.cnv.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider

class CameraManager(
    private val activity: AppCompatActivity,
    private val previewView: PreviewView,
) {

    private val viewModel: CameraViewModel =
        ViewModelProvider(activity)[CameraViewModel::class.java]

    private var analyzer: ImageAnalysis.Analyzer? = null

    private val requestCameraPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startPreview()
        }
    }

    fun start(analyzer: ImageAnalysis.Analyzer? = null) {
        this.analyzer = analyzer
        if (hasCameraPermission()) {
            startPreview()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startPreview() {
        viewModel.bindPreview(activity, previewView, activity, analyzer)
    }
}
