package com.example.cnv.replay

import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.replay.facade.ReplayEngineFacade

/**
 * Public Replay Engine entry (STEP 16-2).
 * Delegates entirely to [ReplayEngineFacade]; internals stay hidden.
 *
 * Prefer depending on [ReplayEngineApi] at call sites (Viewer / Analysis / AI / Report).
 */
class ReplayEngine(
    config: ReplayConfig = ReplayConfig.DEFAULT,
    catalog: FactoryCatalog = FactoryCatalog.get(),
) : ReplayEngineApi {

    private val facade: ReplayEngineApi = ReplayEngineFacade(config, catalog)

    override fun loadSession(
        sessionId: String,
        context: ReplayLoadContext,
        onDone: ((success: Boolean, errorMessage: String?) -> Unit)?,
    ) = facade.loadSession(sessionId, context, onDone)

    override fun play() = facade.play()

    override fun pause() = facade.pause()

    override fun stop() = facade.stop()

    override fun restart() = facade.restart()

    override fun seek(frameIndex: Int) = facade.seek(frameIndex)

    override fun seekToTimestampNs(timestampNs: Long) = facade.seekToTimestampNs(timestampNs)

    override fun seekToRoutePositionMm(routeMm: Float) = facade.seekToRoutePositionMm(routeMm)

    override fun seekToEventId(eventId: Long) = facade.seekToEventId(eventId)

    override fun nextEvent() = facade.nextEvent()

    override fun previousEvent() = facade.previousEvent()

    override fun currentEvent(): ReplayFrame? = facade.currentEvent()

    override fun currentSession(): InspectionSessionSummary? = facade.currentSession()

    override fun currentState(): ReplayPlaybackState = facade.currentState()

    override fun currentPosition(): ReplayPosition = facade.currentPosition()

    override fun playbackSpeed(): Float = facade.playbackSpeed()

    override fun setPlaybackSpeed(speed: Float) = facade.setPlaybackSpeed(speed)

    override fun events(): List<ReplayFrame> = facade.events()

    override fun frameCount(): Int = facade.frameCount()

    override fun currentIndex(): Int = facade.currentIndex()

    override fun sessionId(): String? = facade.sessionId()

    override fun errorMessage(): String? = facade.errorMessage()

    override fun addListener(listener: ReplayEngineApi.Listener) = facade.addListener(listener)

    override fun removeListener(listener: ReplayEngineApi.Listener) = facade.removeListener(listener)

    override fun clear() = facade.clear()
}
