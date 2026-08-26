package com.example.cnv.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.cnv.util.AppFileGuard
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rolling 1s back-camera segments + shock-triggered pre/post clip export.
 *
 * Requires [VideoCapture] from [CameraViewModel] (same lifecycle as Preview/Analysis).
 */
class ShockClipRecorder(
    private val context: Context,
) {

    data class ClipResult(
        val sessionId: String,
        val timestampNs: Long,
        val peakG: Float,
        val file: File,
    )

    @Volatile
    private var videoCapture: VideoCapture<androidx.camera.video.Recorder>? = null

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var active = false

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private val ringLock = Any()
    private val ring = ArrayDeque<File>()
    private var activeRecording: Recording? = null
    private var segmentGen = 0
    private val capturing = AtomicBoolean(false)

    private var onClipSaved: ((ClipResult) -> Unit)? = null

    private val bufferDir: File
        get() = File(context.cacheDir, "shock_buffer").also { it.mkdirs() }

    private val clipRoot: File
        get() = File(context.filesDir, "shock_clips").also { it.mkdirs() }

    fun setOnClipSaved(listener: ((ClipResult) -> Unit)?) {
        onClipSaved = listener
    }

    fun attachVideoCapture(capture: VideoCapture<androidx.camera.video.Recorder>?) {
        videoCapture = capture
        if (capture != null && active && sessionId != null) {
            startSegmentLoop()
        }
    }

    fun beginSession(sessionId: String) {
        if (!ShockClipSettingsStore.isEnabled(context)) return
        this.sessionId = sessionId
        active = true
        synchronized(ringLock) { ring.clear() }
        clearBufferDir()
        if (videoCapture != null) {
            startSegmentLoop()
        }
        println("LOG[ShockClip] session started id=$sessionId")
    }

    fun endSession() {
        active = false
        handler.removeCallbacksAndMessages(null)
        runOnMainAndWait { stopActiveRecording() }
        synchronized(ringLock) { ring.clear() }
        sessionId = null
        println("LOG[ShockClip] session ended")
    }

    fun onRecordableShock(timestampNs: Long, peakG: Float) {
        if (!active || !ShockClipSettingsStore.isEnabled(context)) return
        val sid = sessionId ?: return
        if (!capturing.compareAndSet(false, true)) {
            println("LOG[ShockClip] skip shock — capture already running")
            return
        }
        worker.execute {
            runCatching {
                captureClip(sid, timestampNs, peakG)
            }.onFailure {
                println("LOG[ShockClip] capture failed: ${it.message}")
            }
            capturing.set(false)
        }
    }

    fun clipFile(sessionId: String, timestampNs: Long): File {
        require(AppFileGuard.isSafeSessionId(sessionId)) { "Invalid sessionId: $sessionId" }
        return File(clipRoot, "$sessionId/$timestampNs.mp4")
    }

    fun release() {
        endSession()
        worker.shutdown()
    }

    private fun captureClip(sessionId: String, timestampNs: Long, peakG: Float) {
        pauseRollingBuffer()
        try {
            val preSec = ShockClipSettingsStore.preSec(context)
            val postSec = ShockClipSettingsStore.postSec(context)
            val preSegments = snapshotRing(preSec)
            val postFiles = recordPostBufferSegments(postSec)
            val all = preSegments + postFiles
            val out = clipFile(sessionId, timestampNs)
            out.parentFile?.mkdirs()
            val ok = Mp4SegmentMerger.merge(all, out)
            postFiles.forEach { if (it !in preSegments) it.delete() }
            if (ok) {
                println(
                    "LOG[ShockClip][SAVED] session=$sessionId ts=$timestampNs " +
                        "peakG=${"%.2f".format(peakG)} pre=${preSegments.size} post=${postFiles.size} " +
                        "bytes=${out.length()}",
                )
                mainExecutor.execute {
                    onClipSaved?.invoke(ClipResult(sessionId, timestampNs, peakG, out))
                }
            } else {
                println("LOG[ShockClip][FAIL] merge session=$sessionId ts=$timestampNs")
            }
        } finally {
            if (active && sessionId != null && this.sessionId == sessionId) {
                mainExecutor.execute { startSegmentLoop() }
            }
        }
    }

    private fun recordPostBufferSegments(postSec: Float): List<File> {
        val count = kotlin.math.ceil(postSec / ShockClipSettingsStore.SEGMENT_SEC).toInt()
            .coerceAtLeast(1)
        val out = ArrayList<File>(count)
        repeat(count) {
            val file = recordOneSegmentBlocking() ?: return@repeat
            out.add(file)
        }
        return out
    }

    private fun recordOneSegmentBlocking(): File? {
        val capture = videoCapture ?: return null
        val file = File(bufferDir, "post_${segmentGen++}_${System.nanoTime()}.mp4")
        val latch = CountDownLatch(1)
        var result: File? = null
        mainExecutor.execute {
            runCatching {
                stopActiveRecording()
                val output = FileOutputOptions.Builder(file).build()
                activeRecording = capture.output
                    .prepareRecording(context, output)
                    .start(mainExecutor) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            if (!event.hasError() && file.exists() && file.length() > 0L) {
                                result = file
                            }
                            latch.countDown()
                        }
                    }
                handler.postDelayed({
                    activeRecording?.stop()
                    activeRecording = null
                }, segmentDurationMs())
            }.onFailure {
                latch.countDown()
            }
        }
        if (!latch.await(segmentDurationMs() + 2_000L, TimeUnit.MILLISECONDS)) {
            println("LOG[ShockClip] segment record timeout")
            mainExecutor.execute { stopActiveRecording() }
            return null
        }
        return result
    }

    private fun startSegmentLoop() {
        if (!active || videoCapture == null || capturing.get()) return
        handler.removeCallbacks(segmentRunnable)
        handler.post(segmentRunnable)
    }

    private val segmentRunnable = object : Runnable {
        override fun run() {
            if (!active || videoCapture == null || capturing.get()) return
            recordRollingSegment {
                if (active && !capturing.get()) {
                    handler.postDelayed(this, segmentDurationMs())
                }
            }
        }
    }

    private fun recordRollingSegment(onDone: () -> Unit) {
        val capture = videoCapture ?: run {
            onDone()
            return
        }
        stopActiveRecording()
        val file = File(bufferDir, "seg_${segmentGen++}_${System.nanoTime()}.mp4")
        val output = FileOutputOptions.Builder(file).build()
        activeRecording = capture.output
            .prepareRecording(context, output)
            .start(mainExecutor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    if (!event.hasError() && file.exists() && file.length() > 0L) {
                        pushRing(file)
                    } else {
                        file.delete()
                    }
                    onDone()
                }
            }
        handler.postDelayed({
            activeRecording?.stop()
        }, segmentDurationMs())
    }

    private fun pushRing(file: File) {
        synchronized(ringLock) {
            ring.addLast(file)
            while (ring.size > maxRingSegments()) {
                ring.removeFirst().delete()
            }
        }
    }

    private fun snapshotRing(preSec: Float): List<File> {
        val maxSegments = kotlin.math.ceil(preSec / ShockClipSettingsStore.SEGMENT_SEC).toInt()
            .coerceAtLeast(1)
        synchronized(ringLock) {
            if (ring.size <= maxSegments) return ring.toList()
            return ring.takeLast(maxSegments)
        }
    }

    private fun maxRingSegments(): Int {
        val pre = ShockClipSettingsStore.preSec(context)
        return kotlin.math.ceil(pre / ShockClipSettingsStore.SEGMENT_SEC).toInt().coerceAtLeast(1)
    }

    private fun segmentDurationMs(): Long =
        (ShockClipSettingsStore.SEGMENT_SEC * 1000f).toLong()

    private fun pauseRollingBuffer() {
        runOnMainAndWait {
            handler.removeCallbacks(segmentRunnable)
            stopActiveRecording()
        }
    }

    private fun runOnMainAndWait(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        mainExecutor.execute {
            runCatching { block() }
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
    }

    private fun stopActiveRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun clearBufferDir() {
        bufferDir.listFiles()?.forEach { it.delete() }
    }
}
