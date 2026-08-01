package com.example.cnv.imu

import android.os.Handler
import android.os.HandlerThread
import com.example.cnv.core.config.IMUConfig
import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.EventDispatcher
import com.example.cnv.core.event.ShockEvent
import kotlin.math.sqrt

/**
 * Processes raw sensor samples off the sensor callback thread.
 */
class IMUProcessor(
    private val config: IMUConfig,
    private val repository: IMURepository,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
) {

    private val gravityFilter = GravityFilter(config)
    private val shockDetector = ShockDetector(config)

    private val accel = FloatArray(GravityFilter.VECTOR_SIZE)
    private val gyro = FloatArray(GravityFilter.VECTOR_SIZE)
    private val gravity = FloatArray(GravityFilter.VECTOR_SIZE)
    private val linear = FloatArray(GravityFilter.VECTOR_SIZE)

    private var hasAccel = false
    private var hasGyro = false
    private var accelAccuracy = SensorAccuracy.UNKNOWN
    private var gyroAccuracy = SensorAccuracy.UNKNOWN

    private var sampleCount = 0
    private var rateWindowStartNs = 0L
    private var samplingRateHz = 0f

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    fun start() {
        if (workerThread != null) {
            return
        }
        val thread = HandlerThread(WORKER_THREAD_NAME)
        thread.start()
        workerThread = thread
        workerHandler = Handler(thread.looper)
        rateWindowStartNs = 0L
        sampleCount = 0
    }

    fun stop() {
        workerHandler?.removeCallbacksAndMessages(null)
        workerThread?.quitSafely()
        workerHandler = null
        workerThread = null
        gravityFilter.reset()
        shockDetector.reset()
        hasAccel = false
        hasGyro = false
    }

    /** Lightweight enqueue from sensor callback — no heavy work here. */
    fun onAccelerometer(timestampNs: Long, values: FloatArray, accuracy: SensorAccuracy) {
        val handler = workerHandler ?: return
        val x = values.getOrElse(0) { 0f }
        val y = values.getOrElse(1) { 0f }
        val z = values.getOrElse(2) { 0f }
        handler.post {
            accel[0] = x
            accel[1] = y
            accel[2] = z
            accelAccuracy = accuracy
            hasAccel = true
            processLocked(timestampNs)
        }
    }

    fun onGyroscope(timestampNs: Long, values: FloatArray, accuracy: SensorAccuracy) {
        val handler = workerHandler ?: return
        val x = values.getOrElse(0) { 0f }
        val y = values.getOrElse(1) { 0f }
        val z = values.getOrElse(2) { 0f }
        handler.post {
            gyro[0] = x
            gyro[1] = y
            gyro[2] = z
            gyroAccuracy = accuracy
            hasGyro = true
            processLocked(timestampNs)
        }
    }

    private fun processLocked(timestampNs: Long) {
        if (!hasAccel) {
            return
        }
        gravityFilter.process(accel, gravity, linear)
        updateSamplingRate(timestampNs)

        val linearMag = GravityFilter.magnitude(linear[0], linear[1], linear[2])
        val gyroMag = if (hasGyro) {
            sqrt(gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2])
        } else {
            0f
        }

        val shockEvent = shockDetector.onSample(timestampNs, linearMag, gyroMag)
        val shockLevel = if (shockEvent != null) {
            shockEvent.confidence
        } else {
            (linearMag / config.shockAccelerationThreshold).coerceIn(0f, 1f)
        }
        val confidence = shockEvent?.confidence
            ?: (1f - (linearMag / (config.shockAccelerationThreshold * 2f)).coerceIn(0f, 1f))

        repository.update(
            IMUData(
                timestampNs = timestampNs,
                accelerometerX = accel[0],
                accelerometerY = accel[1],
                accelerometerZ = accel[2],
                gyroscopeX = gyro[0],
                gyroscopeY = gyro[1],
                gyroscopeZ = gyro[2],
                gravityX = gravity[0],
                gravityY = gravity[1],
                gravityZ = gravity[2],
                linearAccelerationX = linear[0],
                linearAccelerationY = linear[1],
                linearAccelerationZ = linear[2],
                confidence = confidence,
                shockLevel = shockLevel,
                samplingRateHz = samplingRateHz,
                accelerometerAccuracy = accelAccuracy,
                gyroscopeAccuracy = gyroAccuracy,
            ),
        )

        if (shockEvent != null) {
            eventDispatcher.dispatch(
                ShockEvent(
                    timestampNs = shockEvent.timestampNs,
                    peakAcceleration = shockEvent.peakAcceleration,
                    peakGyroscope = shockEvent.peakGyroscope,
                    durationNs = shockEvent.durationNs,
                    confidence = shockEvent.confidence,
                ),
            )
        }
    }

    private fun updateSamplingRate(timestampNs: Long) {
        if (rateWindowStartNs == 0L) {
            rateWindowStartNs = timestampNs
            sampleCount = 0
            return
        }
        sampleCount++
        val elapsed = timestampNs - rateWindowStartNs
        if (elapsed >= RATE_WINDOW_NS) {
            samplingRateHz = sampleCount * 1_000_000_000f / elapsed
            rateWindowStartNs = timestampNs
            sampleCount = 0
        }
    }

    companion object {
        private const val WORKER_THREAD_NAME = "cnv-imu-processor"
        private const val RATE_WINDOW_NS = 1_000_000_000L
    }
}
