package com.example.cnv.production

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference

/**
 * Recovery hooks for Camera / Replay / Room (STEP 20).
 * Callers register actions; failures are isolated so the app does not crash.
 */
object RecoveryCoordinator {

    private val cameraReinit = AtomicReference<(() -> Unit)?>(null)
    private val replayReload = AtomicReference<(() -> Unit)?>(null)

    fun registerCameraReinit(action: (() -> Unit)?) {
        cameraReinit.set(action)
    }

    fun registerReplayReload(action: (() -> Unit)?) {
        replayReload.set(action)
    }

    fun recoverCamera(reason: String = "stall") {
        ProductionLog.warning("CNV.Recovery", "Camera reinit: $reason")
        ProductionMetrics.recordCameraRecovery()
        runCatching { cameraReinit.get()?.invoke() }
            .onFailure { ProductionLog.error("CNV.Recovery", "Camera reinit failed", it) }
    }

    fun recoverReplay(reason: String = "error") {
        ProductionLog.warning("CNV.Recovery", "Replay reload: $reason")
        ProductionMetrics.recordReplayRecovery()
        runCatching { replayReload.get()?.invoke() }
            .onFailure { ProductionLog.error("CNV.Recovery", "Replay reload failed", it) }
    }

    /**
     * Room retry with short backoff. Must run on a background thread.
     */
    fun <T> withRoomRetry(
        attempts: Int = DEFAULT_ATTEMPTS,
        delayMs: Long = DEFAULT_DELAY_MS,
        block: () -> T,
    ): T {
        var last: Throwable? = null
        repeat(attempts) { index ->
            try {
                return block()
            } catch (t: Throwable) {
                last = t
                ProductionMetrics.recordRoomRetry()
                ProductionLog.warning(
                    "CNV.Recovery",
                    "Room retry ${index + 1}/$attempts: ${t.message}",
                )
                if (index < attempts - 1) {
                    SystemClock.sleep(delayMs * (index + 1))
                }
            }
        }
        throw last ?: IllegalStateException("Room retry exhausted")
    }

    const val DEFAULT_ATTEMPTS = 3
    const val DEFAULT_DELAY_MS = 40L
}
