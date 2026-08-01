package com.example.cnv.opencv

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * CameraX analyzer: ImageProxy → gray → DistanceEstimator pipeline → Bitmap.
 * Uses a double-buffer Bitmap pool to avoid allocating every frame.
 */
class GrayScaleFrameAnalyzer(
    private val distanceEstimator: DistanceEstimator,
    private val onProcessedFrame: (bitmap: Bitmap, result: DistanceEstimateResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val orbFeatureDetector = OrbFeatureDetector()

    private var writeBitmap: Bitmap? = null
    private var displayBitmap: Bitmap? = null

    override fun analyze(imageProxy: ImageProxy) {
        var grayMat: Mat? = null
        var overlayMat: Mat? = null
        try {
            grayMat = ImageProxyMatConverter.toGrayMat(imageProxy)
            val keypoints = orbFeatureDetector.detect(grayMat!!)
            val estimate = distanceEstimator.estimate(grayMat!!, keypoints)
            overlayMat = estimate.first
            val distanceResult = estimate.second

            val width = overlayMat!!.cols()
            val height = overlayMat!!.rows()
            val target = obtainWriteBitmap(width, height)
            Utils.matToBitmap(overlayMat, target)

            // TODO: Full rotation-aware processing for non-fixed mounts (STEP later).
            // Assumes the device remains in a fixed mount orientation for production use.
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val output = if (rotationDegrees == 0) {
                swapBuffers(target)
            } else {
                rotateIntoDisplayBuffer(target, rotationDegrees.toFloat())
            }
            onProcessedFrame(output, distanceResult)
        } finally {
            grayMat?.release()
            overlayMat?.release()
            imageProxy.close()
        }
    }

    private fun obtainWriteBitmap(width: Int, height: Int): Bitmap {
        val existing = writeBitmap
        if (existing != null &&
            !existing.isRecycled &&
            existing.width == width &&
            existing.height == height
        ) {
            return existing
        }
        existing?.recycle()
        val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        writeBitmap = created
        return created
    }

    private fun swapBuffers(written: Bitmap): Bitmap {
        val previousDisplay = displayBitmap
        displayBitmap = written
        writeBitmap = previousDisplay
        return written
    }

    private fun rotateIntoDisplayBuffer(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        displayBitmap?.recycle()
        displayBitmap = rotated
        return rotated
    }
}
