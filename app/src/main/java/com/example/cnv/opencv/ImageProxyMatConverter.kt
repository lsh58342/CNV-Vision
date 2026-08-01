package com.example.cnv.opencv

import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Converts a CameraX [ImageProxy] (YUV_420_888) Y plane into a grayscale [Mat].
 */
object ImageProxyMatConverter {

    fun toGrayMat(imageProxy: ImageProxy): Mat {
        val width = imageProxy.width
        val height = imageProxy.height
        val yPlane = imageProxy.planes[0]
        val rowStride = yPlane.rowStride
        val buffer = yPlane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        if (rowStride == width) {
            val gray = Mat(height, width, CvType.CV_8UC1)
            gray.put(0, 0, data)
            return gray
        }

        val padded = Mat(height, rowStride, CvType.CV_8UC1)
        try {
            padded.put(0, 0, data)
            return padded.colRange(0, width).clone()
        } finally {
            padded.release()
        }
    }
}
