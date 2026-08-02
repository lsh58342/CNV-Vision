package com.example.cnv.replay

import com.example.cnv.inspection.InspectionSessionSummary

/**
 * Public Replay Engine API (STEP 16-2).
 * Viewer / Analysis / future AI / Report must use only this surface.
 * Internal cache / controllers / state machine are not part of this API.
 */
interface ReplayEngineApi {

    fun loadSession(
        sessionId: String,
        context: ReplayLoadContext = ReplayLoadContext(),
        onDone: ((success: Boolean, errorMessage: String?) -> Unit)? = null,
    )

    fun play()
    fun pause()
    fun stop()
    fun restart()

    fun seek(frameIndex: Int)
    fun seekToTimestampNs(timestampNs: Long)
    fun seekToRoutePositionMm(routeMm: Float)
    fun seekToEventId(eventId: Long)

    fun nextEvent()
    fun previousEvent()

    fun currentEvent(): ReplayFrame?
    fun currentSession(): InspectionSessionSummary?
    fun currentState(): ReplayPlaybackState
    fun currentPosition(): ReplayPosition
    fun playbackSpeed(): Float
    fun setPlaybackSpeed(speed: Float)

    /**
     * Read-only event list for Analysis / AI / Report.
     * Backed by Engine cache — callers must not treat this as Room access.
     */
    fun events(): List<ReplayFrame>

    fun frameCount(): Int
    fun currentIndex(): Int
    fun sessionId(): String?
    fun errorMessage(): String?

    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
    fun clear()

    fun interface Listener {
        fun onReplayChanged()
    }
}
