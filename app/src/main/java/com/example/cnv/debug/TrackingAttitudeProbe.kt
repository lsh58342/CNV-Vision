package com.example.cnv.debug

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Debug-only attitude probe (STEP 20-22).
 * Registers gyroscope (+ game rotation vector when available) independently of IMUManager
 * so HUD can show listener state / yaw / pitch / roll without changing shock algorithms.
 */
class TrackingAttitudeProbe(
    context: Context,
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotation: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    @Volatile
    var gyroRegistered: Boolean = false
        private set

    @Volatile
    var yawDeg: Float = 0f
        private set

    @Volatile
    var pitchDeg: Float = 0f
        private set

    @Volatile
    var rollDeg: Float = 0f
        private set

    /** Integrated yaw from gyroscope Z (deg) — proves gyro samples are consumed. */
    @Volatile
    var gyroIntegratedHeadingDeg: Float = 0f
        private set

    private var lastGyroNs: Long = 0L
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        if (gyroRegistered) return
        var ok = false
        gyroscope?.let {
            ok = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotation?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroRegistered = ok && gyroscope != null
        println(
            "LOG[TrackingDebug][GYRO] listenerRegistered=$gyroRegistered " +
                "hasGyro=${gyroscope != null} hasRotation=${rotation != null}",
        )
    }

    fun stop() {
        if (!gyroRegistered && rotation == null) {
            sensorManager.unregisterListener(this)
            return
        }
        sensorManager.unregisterListener(this)
        gyroRegistered = false
        lastGyroNs = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val ts = event.timestamp
                if (lastGyroNs > 0L) {
                    val dt = (ts - lastGyroNs) * NS_TO_SEC
                    if (dt > 0.0 && dt < 0.5) {
                        // Device Z ≈ yaw when phone is nearly upright in portrait mount.
                        val wz = event.values.getOrElse(2) { 0f }
                        gyroIntegratedHeadingDeg =
                            normalizeDeg(gyroIntegratedHeadingDeg + Math.toDegrees(wz * dt).toFloat())
                    }
                }
                lastGyroNs = ts
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR,
            -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                yawDeg = normalizeDeg(Math.toDegrees(orientation[0].toDouble()).toFloat())
                pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
                // Prefer game-rotation yaw as gyro heading when available.
                if (rotation != null) {
                    gyroIntegratedHeadingDeg = yawDeg
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val NS_TO_SEC = 1e-9
        fun normalizeDeg(deg: Float): Float {
            var d = deg % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }

        fun headingFromDelta(dx: Double, dy: Double): Float {
            if (absHyp(dx, dy) < 1e-6) return 0f
            return normalizeDeg(Math.toDegrees(atan2(dy, dx)).toFloat())
        }

        private fun absHyp(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)

        fun blendHeading(aDeg: Float, bDeg: Float, wA: Float): Float {
            val ra = Math.toRadians(aDeg.toDouble())
            val rb = Math.toRadians(bDeg.toDouble())
            val wB = 1f - wA
            val x = wA * cos(ra) + wB * cos(rb)
            val y = wA * sin(ra) + wB * sin(rb)
            return normalizeDeg(Math.toDegrees(atan2(y, x)).toFloat())
        }
    }
}
