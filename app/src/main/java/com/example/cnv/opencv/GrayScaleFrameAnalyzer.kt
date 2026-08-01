package com.example.cnv.opencv

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * CameraX analyzer: ImageProxy → OpenCV Mat → grayscale Bitmap.
 */
class GrayScaleFrameAnalyzer(
    private val onGrayBitmap: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val grayMat: Mat = ImageProxyMatConverter.toGrayMat(imageProxy)
            val bitmap = Bitmap.createBitmap(
                grayMat.cols(),
                grayMat.rows(),
                Bitmap.Config.ARGB_8888,
            )
            Utils.matToBitmap(grayMat, bitmap)
            grayMat.release()

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
            onGrayBitmap(output)
        } finally {
            imageProxy.close()
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
