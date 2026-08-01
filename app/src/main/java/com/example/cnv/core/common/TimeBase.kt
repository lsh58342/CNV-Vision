package com.example.cnv.core.common

import android.os.SystemClock

/**
 * Single time base for Camera / IMU / Fusion / Map / Inspection timestamps.
 * Uses elapsed-realtime nanoseconds (same domain as [android.hardware.SensorEvent.timestamp]
 * and CameraX [androidx.camera.core.ImageInfo.getTimestamp] on typical devices).
 *
 * Do not mix with [System.nanoTime].
 */
object TimeBase {
    fun nowNs(): Long = SystemClock.elapsedRealtimeNanos()
}
