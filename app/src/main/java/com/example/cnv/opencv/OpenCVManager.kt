package com.example.cnv.opencv

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.config.CalibrationManager

/**
 * OpenCV entry point: library init, distance estimator, UI binding.
 * Lifecycle: start analyzer → (camera binds separately) → stop: deactivate → release analyzer → reset OpenCV state.
 */
class OpenCVManager(
    private val activity: AppCompatActivity,
    private var grayImageView: ImageView,
) {

    private val viewModel: OpenCVViewModel =
        ViewModelProvider(activity)[OpenCVViewModel::class.java]

    private val distanceEstimator: DistanceEstimator =
        OpticalFlowDistanceEstimator(CalibrationManager.getInstance(activity))

    private var analyzer: GrayScaleFrameAnalyzer? = null
    private var observing: Boolean = false

    /** Rebind gray preview for Developer screen migration (wiring only). */
    fun attachGrayImageView(view: ImageView) {
        grayImageView = view
    }

    fun start(): ImageAnalysis.Analyzer? {
        if (!viewModel.initialize()) {
            return null
        }

        if (!observing) {
            viewModel.grayFrame.observe(activity) { bitmap ->
                val target = grayImageView
                val previous = (target.drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null) {
                    target.setImageDrawable(null)
                    if (previous != null && !previous.isRecycled) {
                        previous.recycle()
                    }
                    viewModel.onGrayFrameDisplayed(null)
                    return@observe
                }
                target.setImageBitmap(bitmap)
                viewModel.onGrayFrameDisplayed(bitmap)
                if (previous != null &&
                    previous !== bitmap &&
                    !previous.isRecycled
                ) {
                    previous.recycle()
                }
            }
            observing = true
        }

        val existing = analyzer
        if (existing != null) {
            existing.setActive(true)
            return existing
        }

        val created = GrayScaleFrameAnalyzer(distanceEstimator) { bitmap, result ->
            viewModel.publishGrayFrame(bitmap)
            viewModel.publishDistanceResult(result)
        }
        analyzer = created
        return created
    }

    /**
     * Ordered shutdown for STEP 10-5:
     * 1) deactivate analyzer (no further UI callbacks after this returns for new frames)
     * 2) release analyzer bitmap pool
     * 3) reset OpenCV estimator resources
     *
     * Call only after Camera has been unbound.
     */
    fun release() {
        val current = analyzer
        current?.setActive(false)
        current?.release()
        analyzer = null
        distanceEstimator.reset()
        viewModel.clearFrames()
    }
}
