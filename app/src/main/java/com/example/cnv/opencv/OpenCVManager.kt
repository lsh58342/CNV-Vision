package com.example.cnv.opencv

import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.config.CalibrationManager

/**
 * OpenCV entry point: library init, distance estimator, UI binding.
 */
class OpenCVManager(
    private val activity: AppCompatActivity,
    private val grayImageView: ImageView,
) {

    private val viewModel: OpenCVViewModel =
        ViewModelProvider(activity)[OpenCVViewModel::class.java]

    private val distanceEstimator: DistanceEstimator =
        OpticalFlowDistanceEstimator(CalibrationManager.getInstance(activity))

    fun start(): ImageAnalysis.Analyzer? {
        if (!viewModel.initialize()) {
            return null
        }

        viewModel.grayFrame.observe(activity) { bitmap ->
            grayImageView.setImageBitmap(bitmap)
        }

        return GrayScaleFrameAnalyzer(distanceEstimator) { bitmap, result ->
            viewModel.publishGrayFrame(bitmap)
            viewModel.publishDistanceResult(result)
        }
    }
}
