package com.example.cnv.replay

import com.example.cnv.inspection.InspectionSessionSummary

/**
 * Replay Engine interface (STEP 16-3).
 *
 * Implementations: [DefaultReplayEngine] (current), Mock / Remote (future).
 * Viewer / Analysis / AI / Report / Export depend on this interface only.
 */
interface ReplayEngine {

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

    fun setPlaybackSpeed(speed: Float)
    fun playbackSpeed(): Float

    fun currentEvent(): ReplayFrame?
    fun currentSession(): InspectionSessionSummary?
    fun currentState(): ReplayPlaybackState

    /** Timeline position (timestamp / elapsed / progress / drawing). */
    fun currentTimelinePosition(): ReplayPosition

    /** Route position in mm along the laid-out route. */
    fun currentRoutePositionMm(): Float

    /** Engine statistics via internal StatisticsProvider. */
    fun currentStatistics(): ReplayEngineStatistics

    /**
     * Read-only event snapshot for Analysis / AI / Report.
     * Does not expose the internal cache type.
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
