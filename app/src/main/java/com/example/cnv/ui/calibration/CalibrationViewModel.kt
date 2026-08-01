package com.example.cnv.ui.calibration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cnv.config.CalibrationManager

class CalibrationViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val calibrationManager = CalibrationManager.getInstance(application)

    private val _sessionPixel = MutableLiveData(0f)
    val sessionPixel: LiveData<Float> = _sessionPixel

    private val _mmPerPixel = MutableLiveData(0f)
    val mmPerPixel: LiveData<Float> = _mmPerPixel

    private val _sessionActive = MutableLiveData(false)
    val sessionActive: LiveData<Boolean> = _sessionActive

    private val _isCalibrated = MutableLiveData(false)
    val isCalibrated: LiveData<Boolean> = _isCalibrated

    private val _statusMessage = MutableLiveData("")
    val statusMessage: LiveData<String> = _statusMessage

    fun refresh() {
        calibrationManager.reload()
        _sessionPixel.value = calibrationManager.getSessionAccumulatedPixel()
        _mmPerPixel.value = calibrationManager.getMmPerPixel()
        _sessionActive.value = calibrationManager.isCalibrationSessionActive()
        _isCalibrated.value = calibrationManager.isCalibrated()
    }

    fun startCalibration() {
        calibrationManager.startCalibration()
        refresh()
        _statusMessage.value = "Calibration session started. Move the device, then enter real distance."
    }

    fun finishCalibration(realDistanceMm: Float): Boolean {
        if (realDistanceMm <= 0f) {
            _statusMessage.value = "Enter a positive real distance (mm)."
            return false
        }
        val finished = calibrationManager.finishCalibration(realDistanceMm)
        refresh()
        _statusMessage.value = if (finished) {
            "Calibration saved."
        } else {
            "Finish failed. Start a session and accumulate pixel distance first."
        }
        return finished
    }

    fun cancelCalibration() {
        calibrationManager.cancelCalibration()
        refresh()
        _statusMessage.value = "Calibration session cancelled."
    }

    fun resetCalibration() {
        calibrationManager.resetCalibration()
        refresh()
        _statusMessage.value = "Saved calibration cleared."
    }
}
