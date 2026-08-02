package com.example.cnv.opencv

import androidx.camera.core.ImageProxy
import org.opencv.core.Mat

/**
 * Converts a CameraX [ImageProxy] (YUV_420_888) Y plane into a grayscale [Mat].
 * Uses [GrayMatPool] to reuse Mat / byte buffers (STEP 20) — same output semantics.
 */
object ImageProxyMatConverter {

    fun toGrayMat(imageProxy: ImageProxy): Mat {
        val width = imageProxy.width
        val height = imageProxy.height
        val yPlane = imageProxy.planes[0]
        val rowStride = yPlane.rowStride
        val buffer = yPlane.buffer
        val remaining = buffer.remaining()
        val data = GrayMatPool.obtainBuffer(remaining)
        buffer.get(data, 0, remaining)
        // Rewind not required — ImageProxy closes after analyze.

        if (rowStride == width) {
            val gray = GrayMatPool.obtainGray(width, height)
            gray.put(0, 0, data)
            return gray
        }

        val padded = GrayMatPool.obtainPadded(height, rowStride)
        try {
            padded.put(0, 0, data)
            // Caller owns the clone; padded returns to pool.
            return padded.colRange(0, width).clone()
        } finally {
            GrayMatPool.recyclePadded(padded)
        }
    }

    /** Recycle a Mat produced by [toGrayMat] when rowStride == width (pooled). */
    fun recycleIfPooled(gray: Mat?) {
        GrayMatPool.recycleGray(gray)
    }
}
