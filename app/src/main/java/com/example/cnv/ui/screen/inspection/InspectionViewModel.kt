package com.example.cnv.ui.screen.inspection

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.inspection.InspectionResult

/**
 * Inspection Screen ViewModel — UI state only.
 * Delegates start/stop / status reads to [InspectionPipeline] (existing engines).
 */
class InspectionViewModel(
    private val pipeline: InspectionPipeline,
) : ViewModel() {

    private val _status = MutableLiveData(InspectionUiStatus())
    val status: LiveData<InspectionUiStatus> = _status

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            _status.value = pipeline.readStatus()
            handler.postDelayed(this, 250L)
        }
    }

    private var polling = false

    fun attachPreview(preview: PreviewView) {
        pipeline.attachPreview(preview)
        startPolling()
    }

    fun detachPreview() {
        stopPolling()
        pipeline.detach()
    }

    /** Calls existing InspectionManager.start — no algorithm change. */
    fun startInspection(): Boolean = pipeline.startSession().also {
        _status.value = pipeline.readStatus()
    }

    /** Calls existing InspectionManager.stop — no algorithm change. */
    fun stopInspection(): InspectionResult? {
        val result = pipeline.stopSession()
        _status.value = pipeline.readStatus()
        return result
    }

    override fun onCleared() {
        stopPolling()
        pipeline.detach()
        super.onCleared()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.post(pollRunnable)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(pollRunnable)
    }

    class Factory(
        private val activity: AppCompatActivity,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InspectionViewModel::class.java)) {
                return InspectionViewModel(InspectionPipeline(activity)) as T
            }
            error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
