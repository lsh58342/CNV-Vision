package com.example.cnv.opencv

import org.opencv.core.CvType
import org.opencv.core.Mat
import java.util.concurrent.atomic.AtomicReference

/**
 * Reusable grayscale Mat + Y-plane buffer (STEP 20 memory).
 * Does not change conversion semantics — only reduces allocations.
 */
internal object GrayMatPool {

    private val grayRef = AtomicReference<Mat?>(null)
    private val paddedRef = AtomicReference<Mat?>(null)
    private val bufferRef = AtomicReference<ByteArray?>(null)

    fun obtainGray(width: Int, height: Int): Mat {
        val existing = grayRef.getAndSet(null)
        if (existing != null &&
            !existing.empty() &&
            existing.cols() == width &&
            existing.rows() == height &&
            existing.type() == CvType.CV_8UC1
        ) {
            return existing
        }
        existing?.release()
        return Mat(height, width, CvType.CV_8UC1)
    }

    fun recycleGray(mat: Mat?) {
        if (mat == null || mat.empty()) {
            mat?.release()
            return
        }
        val previous = grayRef.getAndSet(mat)
        previous?.release()
    }

    fun obtainPadded(height: Int, rowStride: Int): Mat {
        val existing = paddedRef.getAndSet(null)
        if (existing != null &&
            !existing.empty() &&
            existing.cols() == rowStride &&
            existing.rows() == height &&
            existing.type() == CvType.CV_8UC1
        ) {
            return existing
        }
        existing?.release()
        return Mat(height, rowStride, CvType.CV_8UC1)
    }

    fun recyclePadded(mat: Mat?) {
        if (mat == null || mat.empty()) {
            mat?.release()
            return
        }
        val previous = paddedRef.getAndSet(mat)
        previous?.release()
    }

    fun obtainBuffer(exactSize: Int): ByteArray {
        val existing = bufferRef.get()
        if (existing != null && existing.size == exactSize) return existing
        val created = ByteArray(exactSize)
        bufferRef.set(created)
        return created
    }

    fun releaseAll() {
        grayRef.getAndSet(null)?.release()
        paddedRef.getAndSet(null)?.release()
        bufferRef.set(null)
    }
}
