package com.example.cnv.production

import android.os.Handler
import android.os.Looper
import com.example.cnv.core.common.TimeBase

/**
 * Monitors Camera / Replay / Sensor / Frame-processing stalls (STEP 20).
 * Triggers recovery hooks; does not change domain algorithms.
 */
class ProductionWatchdog(
    private val cameraStallMs: Long = DEFAULT_CAMERA_STALL_MS,
    private val sensorStallMs: Long = DEFAULT_SENSOR_STALL_MS,
    private val replayStallMs: Long = DEFAULT_REPLAY_STALL_MS,
    private val processStallMs: Long = DEFAULT_PROCESS_STALL_MS,
    private val tickMs: Long = DEFAULT_TICK_MS,
) {

    interface Listener {
        fun onCameraStall()
        fun onSensorStall()
        fun onReplayStall()
        fun onFrameProcessingStall()
    }

    @Volatile
    private var listener: Listener? = null

    @Volatile
    private var cameraExpected = false

    @Volatile
    private var sensorExpected = false

    @Volatile
    private var replayExpected = false

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastCameraRecoveryNs = 0L
    private var lastReplayRecoveryNs = 0L
    private var lastProcessWarnNs = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            checkStalls()
            handler.postDelayed(this, tickMs)
        }
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun setCameraExpected(expected: Boolean) {
        cameraExpected = expected
        if (expected) ProductionMetrics.markCameraFrame()
    }

    fun setSensorExpected(expected: Boolean) {
        sensorExpected = expected
        if (expected) ProductionMetrics.markSensorEvent()
    }

    fun setReplayExpected(expected: Boolean) {
        replayExpected = expected
        if (expected) ProductionMetrics.markReplayActivity()
    }

    fun start() {
        if (running) return
        running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
        ProductionLog.performance("Watchdog started")
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun checkStalls() {
        val now = TimeBase.nowNs()
        val l = listener ?: return

        if (cameraExpected && ProductionMetrics.lastCameraAgeMs() > cameraStallMs) {
            if (now - lastCameraRecoveryNs > RECOVERY_COOLDOWN_NS) {
                lastCameraRecoveryNs = now
                ProductionLog.warning("CNV.Watchdog", "Camera stall detected")
                runCatching { l.onCameraStall() }
                    .onFailure { ProductionLog.error("CNV.Watchdog", "Camera stall handler failed", it) }
            }
        }

        if (sensorExpected && ProductionMetrics.lastSensorAgeMs() > sensorStallMs) {
            ProductionLog.warning("CNV.Watchdog", "Sensor stall detected")
            runCatching { l.onSensorStall() }
                .onFailure { ProductionLog.error("CNV.Watchdog", "Sensor stall handler failed", it) }
        }

        if (replayExpected && ProductionMetrics.lastReplayAgeMs() > replayStallMs) {
            if (now - lastReplayRecoveryNs > RECOVERY_COOLDOWN_NS) {
                lastReplayRecoveryNs = now
                ProductionLog.warning("CNV.Watchdog", "Replay stall detected")
                runCatching { l.onReplayStall() }
                    .onFailure { ProductionLog.error("CNV.Watchdog", "Replay stall handler failed", it) }
            }
        }

        if (cameraExpected && ProductionMetrics.lastProcessAgeMs() > processStallMs) {
            if (now - lastProcessWarnNs > RECOVERY_COOLDOWN_NS) {
                lastProcessWarnNs = now
                ProductionLog.warning("CNV.Watchdog", "Frame processing stall detected")
                runCatching { l.onFrameProcessingStall() }
                    .onFailure { ProductionLog.error("CNV.Watchdog", "Process stall handler failed", it) }
            }
        }
    }

    companion object {
        const val DEFAULT_CAMERA_STALL_MS = 3_000L
        const val DEFAULT_SENSOR_STALL_MS = 2_000L
        const val DEFAULT_REPLAY_STALL_MS = 5_000L
        const val DEFAULT_PROCESS_STALL_MS = 2_500L
        const val DEFAULT_TICK_MS = 1_000L
        private const val RECOVERY_COOLDOWN_NS = 5_000_000_000L

        @Volatile
        private var shared: ProductionWatchdog? = null

        fun shared(): ProductionWatchdog =
            shared ?: synchronized(this) {
                shared ?: ProductionWatchdog().also { shared = it }
            }
    }
}
