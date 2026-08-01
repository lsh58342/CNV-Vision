package com.example.cnv.opencv

import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModelProvider

/**
 * OpenCV entry point: library init, frame analyzer, grayscale UI binding.
 */
class OpenCVManager(
    private val activity: AppCompatActivity,
    private val grayImageView: ImageView,
) {

    private val viewModel: OpenCVViewModel =
        ViewModelProvider(activity)[OpenCVViewModel::class.java]

    fun start(): ImageAnalysis.Analyzer? {
        if (!viewModel.initialize()) {
            return null
        }

        viewModel.grayFrame.observe(activity) { bitmap ->
            grayImageView.setImageBitmap(bitmap)
        }

        return GrayScaleFrameAnalyzer { bitmap ->
            viewModel.publishGrayFrame(bitmap)
        }
    }
}
