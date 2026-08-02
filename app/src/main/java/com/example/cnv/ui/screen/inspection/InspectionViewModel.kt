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
 * STEP 20-1 / 20-16: Live Dashboard + Live Route overlay polled (no engine mutation).
 */
class InspectionViewModel(
    private val pipeline: InspectionPipeline,
) : ViewModel() {

    private val _status = MutableLiveData(InspectionUiStatus())
    val status: LiveData<InspectionUiStatus> = _status

    private val _dashboard = MutableLiveData(LiveInspectionDashboardState())
    val dashboard: LiveData<LiveInspectionDashboardState> = _dashboard

    private val _liveRoute = MutableLiveData(LiveRouteOverlayState())
    val liveRoute: LiveData<LiveRouteOverlayState> = _liveRoute

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            val live = pipeline.readLiveDashboard()
            _dashboard.value = live
            _status.value = live.toUiStatus()
            _liveRoute.value = pipeline.readLiveRouteOverlay()
            handler.postDelayed(this, POLL_INTERVAL_MS)
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
        refreshSnapshots()
    }

    /** Calls existing InspectionManager.stop — no algorithm change. */
    fun stopInspection(): InspectionResult? {
        val result = pipeline.stopSession()
        refreshSnapshots()
        return result
    }

    override fun onCleared() {
        stopPolling()
        pipeline.detach()
        super.onCleared()
    }

    private fun refreshSnapshots() {
        val live = pipeline.readLiveDashboard()
        _dashboard.value = live
        _status.value = live.toUiStatus()
        _liveRoute.value = pipeline.readLiveRouteOverlay()
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

    companion object {
        /** 150ms — matches LiveRouteViewer marker animation window. */
        private const val POLL_INTERVAL_MS = 150L
    }
}
