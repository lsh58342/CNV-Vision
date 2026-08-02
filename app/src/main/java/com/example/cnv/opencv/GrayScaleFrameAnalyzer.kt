package com.example.cnv.opencv

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.cnv.core.common.TimeBase
import com.example.cnv.core.debug.PipelinePerfMonitor
import com.example.cnv.production.ProductionLog
import com.example.cnv.production.ProductionMetrics
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX analyzer: ImageProxy → gray → DistanceEstimator pipeline → Bitmap.
 * Uses a double-buffer Bitmap pool for conversion; UI receives an owned copy.
 * STEP 20: Mat pool recycle, analyzer timing, isolated exceptions.
 */
class GrayScaleFrameAnalyzer(
    private val distanceEstimator: DistanceEstimator,
    private val onProcessedFrame: (bitmap: Bitmap, result: DistanceEstimateResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private val orbFeatureDetector = OrbFeatureDetector()

    private var writeBitmap: Bitmap? = null
    private var displayBitmap: Bitmap? = null

    private val released = AtomicBoolean(false)
    private val active = AtomicBoolean(true)

    fun setActive(enabled: Boolean) {
        active.set(enabled)
    }

    override fun analyze(imageProxy: ImageProxy) {
        ProductionMetrics.analyzerJobBegin()
        if (released.get() || !active.get()) {
            PipelinePerfMonitor.recordDropped(1)
            ProductionMetrics.analyzerJobEnd()
            imageProxy.close()
            return
        }

        var grayMat: Mat? = null
        var overlayMat: Mat? = null
        var grayFromPool = false
        val frameTimestampNs = imageProxy.imageInfo.timestamp
        val startNs = TimeBase.nowNs()
        try {
            if (released.get() || !active.get()) {
                PipelinePerfMonitor.recordDropped(1)
                return
            }
            val gray = ImageProxyMatConverter.toGrayMat(imageProxy)
            grayMat = gray
            // Pooled when contiguous Y plane; cloned mats are released normally.
            grayFromPool = imageProxy.planes[0].rowStride == imageProxy.width
            val keypoints = orbFeatureDetector.detect(gray)
            val estimate = distanceEstimator.estimate(gray, keypoints, frameTimestampNs)
            val overlay = estimate.first
            overlayMat = overlay
            val distanceResult = estimate.second

            if (released.get() || !active.get()) {
                PipelinePerfMonitor.recordDropped(1)
                return
            }

            val width = overlay.cols()
            val height = overlay.rows()
            val target = obtainWriteBitmap(width, height)
            Utils.matToBitmap(overlay, target)

            // TODO: Full rotation-aware processing for non-fixed mounts (STEP later).
            // Assumes the device remains in a fixed mount orientation for production use.
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val pooled = if (rotationDegrees == 0) {
                swapBuffers(target)
            } else {
                rotateIntoDisplayBuffer(target, rotationDegrees.toFloat())
            }
            // UI owns a copy — analyzer must not share pooled buffers with LiveData/ImageView.
            val uiBitmap = pooled.copy(pooled.config ?: Bitmap.Config.ARGB_8888, false)
            onProcessedFrame(uiBitmap, distanceResult)

            val durationMs = (TimeBase.nowNs() - startNs) / 1_000_000.0
            PipelinePerfMonitor.recordFrameProcessed(durationMs, frameTimestampNs)
            ProductionMetrics.recordAnalyzerTime(durationMs)
            ProductionMetrics.markCameraFrame()
        } catch (t: Throwable) {
            PipelinePerfMonitor.recordDropped(1)
            ProductionLog.error("CNV.Analyzer", "Frame analyze failed", t)
        } finally {
            if (grayFromPool) {
                ImageProxyMatConverter.recycleIfPooled(grayMat)
            } else {
                grayMat?.release()
            }
            overlayMat?.release()
            imageProxy.close()
            ProductionMetrics.analyzerJobEnd()
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

    /** Releases pooled Bitmaps. Call when the analyzer is no longer used. */
    fun release() {
        released.set(true)
        active.set(false)
        writeBitmap?.recycle()
        displayBitmap?.recycle()
        writeBitmap = null
        displayBitmap = null
        GrayMatPool.releaseAll()
    }
}
