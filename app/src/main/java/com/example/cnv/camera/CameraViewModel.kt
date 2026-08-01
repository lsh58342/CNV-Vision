package com.example.cnv.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import java.util.concurrent.Executors

class CameraViewModel : ViewModel() {

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        context: Context,
        analyzer: ImageAnalysis.Analyzer? = null,
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { useCase ->
                    useCase.surfaceProvider = previewView.surfaceProvider
                }

                val useCases = mutableListOf<UseCase>(preview)
                if (analyzer != null) {
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(analysisExecutor, analyzer)
                        }
                    useCases.add(imageAnalysis)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    *useCases.toTypedArray(),
                )
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun onCleared() {
        analysisExecutor.shutdown()
        super.onCleared()
    }
}
