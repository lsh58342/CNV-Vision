package com.example.cnv.vio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.cnv.debug.TrackingAttitudeProbe

/**
 * Dedicated IMU listener for VIO (timestamps from SensorEvent).
 * Does not change ShockDetector / IMUManager shock path.
 */
class VisualInertialImuTracker(
    context: Context,
    private val fusion: VisualInertialFusion,
    private val config: VisualInertialConfig = VisualInertialConfig.DEFAULT,
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotation: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    @Volatile
    private var running = false

    private var lastSampleNs: Long = 0L
    private var sampleCount: Int = 0
    private var windowStartNs: Long = 0L
    private var lastRotationYawDeg: Float? = null
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        if (running) return
        VioStateHub.onImuCapabilities(
            gyro = gyroscope != null,
            accel = accelerometer != null,
            rotationVector = rotation != null,
        )
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotation?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        running = true
        windowStartNs = 0L
        sampleCount = 0
        println("LOG[VIO][IMU] started")
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
        println("LOG[VIO][IMU] stopped")
    }

    fun isRunning(): Boolean = running

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val ts = event.timestamp
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                fusion.onGyro(
                    gyroZRadPerSec = event.values.getOrElse(2) { 0f },
                    timestampNs = ts,
                    rotationVectorYawDeg = lastRotationYawDeg,
                )
                publishImu(ts)
                if (sampleCount % 25 == 0) {
                    println(
                        "LOG[VIO][IMU] ts=$ts yaw=${"%.1f".format(fusion.headingDeg())} " +
                            "wzDps=${"%.1f".format(fusion.angularVelocityDegPerSec())} " +
                            "cumRot=${"%.1f".format(fusion.cumulativeRotationDeg())}",
                    )
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Dynamic accel monitored only — never integrated for distance.
                publishImu(ts)
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR,
            -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                lastRotationYawDeg =
                    TrackingAttitudeProbe.normalizeDeg(
                        Math.toDegrees(orientation[0].toDouble()).toFloat(),
                    )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun publishImu(timestampNs: Long) {
        sampleCount++
        if (windowStartNs == 0L) {
            windowStartNs = timestampNs
        }
        val elapsed = (timestampNs - windowStartNs) * 1e-9
        val hz = if (elapsed > 0.5) sampleCount / elapsed.toFloat() else 0f
        if (elapsed > 1.0) {
            windowStartNs = timestampNs
            sampleCount = 0
        }
        VioStateHub.onImuSample(
            headingDeg = fusion.headingDeg() + config.let {
                VioStateHub.mount.yawOffsetDeg
            },
            angularVelocityDegPerSec = fusion.angularVelocityDegPerSec(),
            cumulativeRotationDeg = fusion.cumulativeRotationDeg(),
            imuHz = hz,
        )
        lastSampleNs = timestampNs
    }
}
