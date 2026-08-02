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
import com.example.cnv.production.ProductionLog
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class CameraViewModel : ViewModel() {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val cameraProviderRef = AtomicReference<ProcessCameraProvider?>(null)

    @Volatile
    private var boundAnalyzer: ImageAnalysis.Analyzer? = null

    @Volatile
    private var boundPreviewView: PreviewView? = null

    @Volatile
    private var boundOwner: LifecycleOwner? = null

    @Volatile
    private var isBound: Boolean = false

    fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        context: Context,
        analyzer: ImageAnalysis.Analyzer? = null,
    ) {
        // Skip CameraX rebind when surface + analyzer already active (STEP 20).
        if (isBound &&
            boundOwner === lifecycleOwner &&
            boundPreviewView === previewView &&
            boundAnalyzer === analyzer &&
            cameraProviderRef.get() != null
        ) {
            return
        }
        boundAnalyzer = analyzer
        boundPreviewView = previewView
        boundOwner = lifecycleOwner
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                runCatching {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProviderRef.set(cameraProvider)
                    val preview = Preview.Builder().build().also { useCase ->
                        useCase.surfaceProvider = previewView.surfaceProvider
                    }

                    val useCases = mutableListOf<UseCase>(preview)
                    val currentAnalyzer = boundAnalyzer
                    if (currentAnalyzer != null) {
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor, currentAnalyzer)
                            }
                        useCases.add(imageAnalysis)
                    }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        *useCases.toTypedArray(),
                    )
                    isBound = true
                }.onFailure { err ->
                    isBound = false
                    ProductionLog.error("CNV.Camera", "Camera bind failed", err)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** Force rebind (recovery path). */
    fun rebindIfPossible(context: Context) {
        val owner = boundOwner ?: return
        val preview = boundPreviewView ?: return
        isBound = false
        bindPreview(owner, preview, context, boundAnalyzer)
    }

    /** Stops analysis / preview binding. Safe to call from Activity onStop. */
    fun unbind() {
        isBound = false
        boundAnalyzer = null
        cameraProviderRef.get()?.unbindAll()
    }

    override fun onCleared() {
        unbind()
        analysisExecutor.shutdown()
        super.onCleared()
    }
}
