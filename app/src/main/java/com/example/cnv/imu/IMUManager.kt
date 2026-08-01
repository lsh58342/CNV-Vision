package com.example.cnv.imu

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.cnv.config.IMUConfig

/**
 * Registers accelerometer / gyroscope and forwards samples to [IMUProcessor].
 * Does not reference Camera or OpenCV packages.
 */
class IMUManager(
    context: Context,
    private val config: IMUConfig = IMUConfig.DEFAULT,
    val repository: IMURepository = IMURepository(),
) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val processor = IMUProcessor(config, repository)

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    @Volatile
    private var running = false

    fun start() {
        if (running) {
            return
        }
        processor.start()
        accelerometer?.let {
            sensorManager.registerListener(this, it, config.samplingPeriodUs)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, config.samplingPeriodUs)
        }
        running = true
    }

    fun stop() {
        if (!running) {
            return
        }
        sensorManager.unregisterListener(this)
        processor.stop()
        running = false
    }

    fun isRunning(): Boolean = running

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) {
            return
        }
        val timestampNs = event.timestamp
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                processor.onAccelerometer(
                    timestampNs,
                    event.values,
                    SensorAccuracy.fromAndroid(event.accuracy),
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                processor.onGyroscope(
                    timestampNs,
                    event.values,
                    SensorAccuracy.fromAndroid(event.accuracy),
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Accuracy is read from SensorEvent on each sample.
    }
}
