package com.example.cnv.ui.screen.inspection

import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.debug.TrackingDebugSnapshot
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

    private val _dashboard = MutableLiveData(LiveInspectionDashboardState())
    val dashboard: LiveData<LiveInspectionDashboardState> = _dashboard

    private val _liveRoute = MutableLiveData(LiveRouteOverlayState())
    val liveRoute: LiveData<LiveRouteOverlayState> = _liveRoute

    private val _shockGraph = MutableLiveData(ShockGraphState())
    val shockGraph: LiveData<ShockGraphState> = _shockGraph

    private val _preflight = MutableLiveData(InspectionPreflight.ready())
    val preflight: LiveData<InspectionPreflight> = _preflight

    private val _trackingDebug = MutableLiveData(TrackingDebugSnapshot())
    val trackingDebug: LiveData<TrackingDebugSnapshot> = _trackingDebug

    private val handler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshSnapshots()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private var polling = false

    fun attachPreview(preview: PreviewView) {
        pipeline.attachPreview(preview)
        _preflight.value = pipeline.evaluatePreflight()
        startPolling()
    }

    fun detachPreview() {
        stopPolling()
        pipeline.detach()
    }

    fun startInspection(): InspectionStartResult {
        val result = pipeline.startSession()
        _preflight.value = result.preflight
        refreshSnapshots()
        return result
    }

    fun stopInspection(): InspectionResult? {
        val result = pipeline.stopSession()
        refreshSnapshots()
        _preflight.value = pipeline.evaluatePreflight()
        return result
    }

    fun evaluatePreflight(): InspectionPreflight {
        val p = pipeline.evaluatePreflight()
        _preflight.value = p
        return p
    }

    override fun onCleared() {
        stopPolling()
        pipeline.detach()
        super.onCleared()
    }

    private fun refreshSnapshots() {
        val live = pipeline.readLiveDashboard()
        _dashboard.value = live
        val ui = live.toUiStatus()
        _status.value = ui
        _liveRoute.value = pipeline.readLiveRouteOverlay()
        _shockGraph.value = pipeline.readShockGraph()
        _trackingDebug.value = pipeline.readTrackingDebug(ui.trackingLabel)
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
        private const val POLL_INTERVAL_MS = 150L
    }
}
