package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.imu.IMUData
import com.example.cnv.imu.IMURepository

/**
 * Polls [IMURepository] on the main thread for debug TextView updates.
 * No Bitmap allocation; does not block camera pipeline.
 */
class ImuDebugHud(
    private val textView: TextView,
    private val repository: IMURepository,
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            render(repository.latest())
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    fun start() {
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    fun stop() {
        handler.removeCallbacks(refreshRunnable)
    }

    private fun render(data: IMUData) {
        textView.text = buildString {
            append("IMU Debug\n")
            append(
                "Accel XYZ: %.2f, %.2f, %.2f\n".format(
                    data.accelerometerX,
                    data.accelerometerY,
                    data.accelerometerZ,
                ),
            )
            append(
                "Gyro XYZ: %.2f, %.2f, %.2f\n".format(
                    data.gyroscopeX,
                    data.gyroscopeY,
                    data.gyroscopeZ,
                ),
            )
            append(
                "Gravity XYZ: %.2f, %.2f, %.2f\n".format(
                    data.gravityX,
                    data.gravityY,
                    data.gravityZ,
                ),
            )
            append(
                "Linear Accel XYZ: %.2f, %.2f, %.2f\n".format(
                    data.linearAccelerationX,
                    data.linearAccelerationY,
                    data.linearAccelerationZ,
                ),
            )
            append("Shock Level: %.2f\n".format(data.shockLevel))
            append("Confidence: %.2f\n".format(data.confidence))
            append("Sampling Rate: %.1f Hz".format(data.samplingRateHz))
        }
    }

    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MS = 200L
    }
}
