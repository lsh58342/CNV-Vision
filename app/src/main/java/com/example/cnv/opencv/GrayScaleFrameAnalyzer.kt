package com.example.cnv.opencv

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * CameraX analyzer: ImageProxy → gray Mat → ORB → Lucas-Kanade flow → Bitmap.
 */
class GrayScaleFrameAnalyzer(
    private val onProcessedFrame: (bitmap: Bitmap, movementDistancePx: Float) -> Unit,
) : ImageAnalysis.Analyzer {

    private val orbFeatureDetector = OrbFeatureDetector()
    private val opticalFlow = LucasKanadeOpticalFlow()

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val grayMat: Mat = ImageProxyMatConverter.toGrayMat(imageProxy)
            val keypoints = orbFeatureDetector.detect(grayMat)
            val flowResult = opticalFlow.process(grayMat, keypoints)
            grayMat.release()

            val overlayMat = flowResult.overlay
            val bitmap = Bitmap.createBitmap(
                overlayMat.cols(),
                overlayMat.rows(),
                Bitmap.Config.ARGB_8888,
            )
            Utils.matToBitmap(overlayMat, bitmap)
            overlayMat.release()

            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val output = if (rotationDegrees == 0) {
                bitmap
            } else {
                rotateBitmap(bitmap, rotationDegrees.toFloat()).also {
                    if (it !== bitmap) {
                        bitmap.recycle()
                    }
                }
            }
            onProcessedFrame(output, flowResult.movementDistancePx)
        } finally {
            imageProxy.close()
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
