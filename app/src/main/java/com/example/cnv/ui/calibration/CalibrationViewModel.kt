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

    private val _pixelDistance = MutableLiveData(0f)
    val pixelDistance: LiveData<Float> = _pixelDistance

    private val _mmPerPixel = MutableLiveData(0f)
    val mmPerPixel: LiveData<Float> = _mmPerPixel

    private val _isCalibrated = MutableLiveData(false)
    val isCalibrated: LiveData<Boolean> = _isCalibrated

    private val _statusMessage = MutableLiveData("")
    val statusMessage: LiveData<String> = _statusMessage

    fun refresh() {
        calibrationManager.reload()
        _pixelDistance.value = calibrationManager.getLastPixelDistance()
        _mmPerPixel.value = calibrationManager.getMmPerPixel()
        _isCalibrated.value = calibrationManager.isCalibrated()
    }

    fun saveCalibration(realDistanceMm: Float): Boolean {
        val pixel = calibrationManager.getLastPixelDistance()
        if (realDistanceMm <= 0f) {
            _statusMessage.value = "Enter a positive real distance (mm)."
            return false
        }
        if (pixel <= 0f) {
            _statusMessage.value = "No pixel distance yet. Move the camera on the main screen first."
            return false
        }
        val scale = calibrationManager.calculateMmPerPixel(realDistanceMm, pixel)
        calibrationManager.setMmPerPixel(scale)
        refresh()
        _statusMessage.value = "Calibration saved."
        return true
    }

    fun resetCalibration() {
        calibrationManager.resetCalibration()
        refresh()
        _statusMessage.value = "Calibration reset."
    }
}
