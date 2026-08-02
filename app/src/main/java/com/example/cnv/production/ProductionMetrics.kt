package com.example.cnv.production

import android.os.Debug
import com.example.cnv.core.common.TimeBase
import com.example.cnv.core.debug.PipelinePerfMonitor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide production metrics (STEP 20).
 * Observability only — does not alter Camera / OpenCV / Replay / HeatMap algorithms.
 */
object ProductionMetrics {

    private val lock = Any()

    @Volatile private var analyzerTimeMs: Double = 0.0
    @Volatile private var eventQueueSize: Int = 0
    @Volatile private var replayCacheSize: Int = 0
    @Volatile private var heatOverlayCells: Int = 0
    @Volatile private var sensorEvents: Long = 0L
    @Volatile private var sensorDropped: Long = 0L
    @Volatile private var sensorHz: Double = 0.0
    @Volatile private var roomRetries: Long = 0L
    @Volatile private var cameraRecoveries: Long = 0L
    @Volatile private var replayRecoveries: Long = 0L

    private val lastCameraNs = AtomicLong(0L)
    private val lastSensorNs = AtomicLong(0L)
    private val lastReplayNs = AtomicLong(0L)
    private val lastProcessNs = AtomicLong(0L)
    private val pendingAnalyzerJobs = AtomicInteger(0)

    private var sensorWindowStartNs: Long = 0L
    private var sensorCountInWindow: Int = 0

    fun recordAnalyzerTime(ms: Double) {
        analyzerTimeMs = ms
        lastProcessNs.set(TimeBase.nowNs())
        lastCameraNs.set(TimeBase.nowNs())
    }

    fun markCameraFrame() {
        lastCameraNs.set(TimeBase.nowNs())
    }

    fun markReplayActivity() {
        lastReplayNs.set(TimeBase.nowNs())
    }

    fun markSensorEvent() {
        lastSensorNs.set(TimeBase.nowNs())
        synchronized(lock) {
            sensorEvents++
            val now = TimeBase.nowNs()
            if (sensorWindowStartNs == 0L) sensorWindowStartNs = now
            sensorCountInWindow++
            val elapsed = now - sensorWindowStartNs
            if (elapsed >= 1_000_000_000L) {
                sensorHz = sensorCountInWindow * 1_000_000_000.0 / elapsed
                sensorCountInWindow = 0
                sensorWindowStartNs = now
            }
        }
    }

    fun recordSensorDropped(count: Long = 1L) {
        if (count <= 0L) return
        synchronized(lock) { sensorDropped += count }
    }

    fun setEventQueueSize(size: Int) {
        eventQueueSize = size.coerceAtLeast(0)
    }

    fun setReplayCacheSize(size: Int) {
        replayCacheSize = size.coerceAtLeast(0)
    }

    fun setHeatOverlayCells(count: Int) {
        heatOverlayCells = count.coerceAtLeast(0)
    }

    fun analyzerJobBegin() {
        pendingAnalyzerJobs.incrementAndGet()
        setEventQueueSize(pendingAnalyzerJobs.get())
    }

    fun analyzerJobEnd() {
        pendingAnalyzerJobs.updateAndGet { (it - 1).coerceAtLeast(0) }
        setEventQueueSize(pendingAnalyzerJobs.get())
    }

    fun recordRoomRetry() {
        synchronized(lock) { roomRetries++ }
    }

    fun recordCameraRecovery() {
        synchronized(lock) { cameraRecoveries++ }
        ProductionLog.warning("CNV.Recovery", "Camera recovery triggered")
    }

    fun recordReplayRecovery() {
        synchronized(lock) { replayRecoveries++ }
        ProductionLog.warning("CNV.Recovery", "Replay session reload triggered")
    }

    fun lastCameraAgeMs(): Double = ageMs(lastCameraNs.get())
    fun lastSensorAgeMs(): Double = ageMs(lastSensorNs.get())
    fun lastReplayAgeMs(): Double = ageMs(lastReplayNs.get())
    fun lastProcessAgeMs(): Double = ageMs(lastProcessNs.get())

    fun memoryUsageMb(): Double {
        val used = Debug.getNativeHeapAllocatedSize() +
            (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
        return used / (1024.0 * 1024.0)
    }

    fun snapshot(): Snapshot {
        val pipeline = PipelinePerfMonitor.snapshot()
        return Snapshot(
            fps = pipeline.fps,
            frameTimeMs = pipeline.frameMs,
            frameDrop = pipeline.droppedFrames,
            analyzerTimeMs = analyzerTimeMs,
            memoryUsageMb = memoryUsageMb(),
            eventQueueSize = eventQueueSize,
            replayCacheSize = replayCacheSize,
            heatOverlayCells = heatOverlayCells,
            sensorHz = sensorHz,
            sensorDropped = sensorDropped,
            roomRetries = roomRetries,
            cameraRecoveries = cameraRecoveries,
            replayRecoveries = replayRecoveries,
            cameraStallMs = lastCameraAgeMs(),
            sensorStallMs = lastSensorAgeMs(),
            replayStallMs = lastReplayAgeMs(),
            processStallMs = lastProcessAgeMs(),
        )
    }

    private fun ageMs(lastNs: Long): Double {
        if (lastNs <= 0L) return Double.POSITIVE_INFINITY
        return (TimeBase.nowNs() - lastNs) / 1_000_000.0
    }

    data class Snapshot(
        val fps: Double,
        val frameTimeMs: Double,
        val frameDrop: Long,
        val analyzerTimeMs: Double,
        val memoryUsageMb: Double,
        val eventQueueSize: Int,
        val replayCacheSize: Int,
        val heatOverlayCells: Int,
        val sensorHz: Double,
        val sensorDropped: Long,
        val roomRetries: Long,
        val cameraRecoveries: Long,
        val replayRecoveries: Long,
        val cameraStallMs: Double,
        val sensorStallMs: Double,
        val replayStallMs: Double,
        val processStallMs: Double,
    )
}
