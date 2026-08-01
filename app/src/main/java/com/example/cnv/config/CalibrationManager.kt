package com.example.cnv.config

import android.content.Context
import com.example.cnv.core.common.TimeBase
import com.example.cnv.core.event.CalibrationEvent
import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.EventDispatcher

/**
 * App-wide calibration authority for mmPerPixel.
 * DistanceEstimator must only read [getMmPerPixel] / [isCalibrated] — never compute scale.
 * Session pixel accumulation is owned exclusively by this manager.
 * Publishes [CalibrationEvent] on session lifecycle — never references IMU/Camera.
 */
class CalibrationManager private constructor(
    private val repository: CalibrationRepository,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
) {

    @Volatile
    private var cached: CalibrationData? = repository.loadCalibration()

    @Volatile
    private var sessionActive: Boolean = false

    @Volatile
    private var sessionAccumulatedPixel: Float = 0f

    fun getMmPerPixel(): Float = cached?.mmPerPixel ?: 0f

    fun isCalibrated(): Boolean {
        val value = cached?.mmPerPixel ?: return false
        return value > 0f
    }

    fun getCalibrationData(): CalibrationData? = cached

    fun isCalibrationSessionActive(): Boolean = sessionActive

    fun getSessionAccumulatedPixel(): Float = sessionAccumulatedPixel

    fun startCalibration() {
        sessionActive = true
        sessionAccumulatedPixel = 0f
        publish(CalibrationEvent.Type.STARTED)
    }

    /**
     * Adds one frame's median pixel motion while a calibration session is active.
     * No-op when session is inactive.
     */
    fun addObservedPixelDistance(pixel: Float) {
        if (!sessionActive) {
            return
        }
        if (pixel > 0f) {
            sessionAccumulatedPixel += pixel
        }
    }

    /**
     * Ends the session and persists mmPerPixel = realDistanceMm / sessionAccumulatedPixel.
     */
    fun finishCalibration(realDistanceMm: Float): Boolean {
        if (!sessionActive) {
            return false
        }
        if (realDistanceMm <= 0f || sessionAccumulatedPixel <= 0f) {
            return false
        }
        val mmPerPixel = realDistanceMm / sessionAccumulatedPixel
        val data = CalibrationData(
            mmPerPixel = mmPerPixel,
            totalObservedPixel = sessionAccumulatedPixel,
            calibratedDistanceMm = realDistanceMm,
            calibratedAt = System.currentTimeMillis(),
            version = CalibrationData.CURRENT_VERSION,
        )
        repository.saveCalibration(data)
        cached = data
        sessionActive = false
        sessionAccumulatedPixel = 0f
        publish(CalibrationEvent.Type.FINISHED, data)
        return true
    }

    fun cancelCalibration() {
        sessionActive = false
        sessionAccumulatedPixel = 0f
        publish(CalibrationEvent.Type.CANCELLED)
    }

    fun resetCalibration() {
        cancelCalibrationWithoutEvent()
        repository.resetCalibration()
        cached = null
        publish(CalibrationEvent.Type.RESET)
    }

    fun reload() {
        cached = repository.loadCalibration()
    }

    private fun cancelCalibrationWithoutEvent() {
        sessionActive = false
        sessionAccumulatedPixel = 0f
    }

    private fun publish(type: CalibrationEvent.Type, data: CalibrationData? = cached) {
        eventDispatcher.dispatch(
            CalibrationEvent(
                timestampNs = TimeBase.nowNs(),
                type = type,
                mmPerPixel = data?.mmPerPixel ?: 0f,
                totalObservedPixel = data?.totalObservedPixel ?: sessionAccumulatedPixel,
                calibratedDistanceMm = data?.calibratedDistanceMm ?: 0f,
            ),
        )
    }

    companion object {
        @Volatile
        private var instance: CalibrationManager? = null

        fun getInstance(context: Context): CalibrationManager {
            return instance ?: synchronized(this) {
                instance ?: CalibrationManager(
                    CalibrationRepository(context.applicationContext),
                ).also { instance = it }
            }
        }
    }
}
