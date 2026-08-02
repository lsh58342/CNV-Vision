package com.example.cnv.replay

import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.inspection.InspectionSessionSummary
import com.example.cnv.replay.internal.ReplayEventCache
import com.example.cnv.replay.internal.ReplayPlaybackController
import com.example.cnv.replay.internal.ReplaySessionLoader
import com.example.cnv.replay.internal.ReplayStateMachine
import com.example.cnv.replay.internal.ReplayStatisticsProvider
import com.example.cnv.replay.internal.ReplayTimelineController

/**
 * Default Replay Engine — Facade over internal components (STEP 16-3).
 *
 * External modules must depend on [ReplayEngine], not this class.
 * Construct via [ReplayEngineFactory] for injection.
 */
class DefaultReplayEngine(
    private val config: ReplayConfig = ReplayConfig.DEFAULT,
    catalog: FactoryCatalog = FactoryCatalog.get(),
) : ReplayEngine {

    private val stateMachine = ReplayStateMachine()
    private val cache = ReplayEventCache()
    private val timeline = ReplayTimelineController()
    private val loader = ReplaySessionLoader(catalog)
    private val statisticsProvider = ReplayStatisticsProvider()
    private val playback = ReplayPlaybackController(onTick = { advanceFromPlayback() })

    private val listeners = mutableListOf<ReplayEngine.Listener>()

    @Volatile
    private var lastError: String? = null

    override fun loadSession(
        sessionId: String,
        context: ReplayLoadContext,
        onDone: ((success: Boolean, errorMessage: String?) -> Unit)?,
    ) {
        playback.stop()
        stateMachine.toLoading()
        lastError = null
        notifyListeners()
        loader.loadAsync(sessionId, context) { result ->
            result.fold(
                onSuccess = { loaded ->
                    cache.put(loaded.session.summary, loaded.frames)
                    timeline.bind(loaded.frames, config.defaultIndex)
                    stateMachine.toReady()
                    lastError = null
                    notifyListeners()
                    onDone?.invoke(true, null)
                },
                onFailure = { err ->
                    cache.clear()
                    timeline.clear()
                    stateMachine.toError()
                    lastError = err.message ?: "Load failed"
                    notifyListeners()
                    onDone?.invoke(false, lastError)
                },
            )
        }
    }

    override fun play() {
        if (cache.isEmpty()) return
        val state = stateMachine.state()
        if (state == ReplayPlaybackState.COMPLETED || state == ReplayPlaybackState.STOPPED) {
            timeline.seek(cache.frames(), 0)
        }
        if (!stateMachine.toPlaying()) {
            if (state != ReplayPlaybackState.READY && state != ReplayPlaybackState.PAUSED) {
                return
            }
            stateMachine.toPlaying()
        }
        playback.play()
        schedulePlayback()
        notifyListeners()
    }

    override fun pause() {
        if (!stateMachine.toPaused()) return
        playback.pause()
        notifyListeners()
    }

    override fun stop() {
        playback.stop()
        stateMachine.toStopped()
        timeline.seek(cache.frames(), 0)
        notifyListeners()
    }

    override fun restart() {
        playback.stop()
        if (cache.isEmpty()) return
        timeline.seek(cache.frames(), 0)
        stateMachine.toReady()
        play()
    }

    override fun seek(frameIndex: Int) {
        if (cache.isEmpty()) return
        timeline.seek(cache.frames(), frameIndex)
        if (stateMachine.state() == ReplayPlaybackState.COMPLETED) {
            stateMachine.toReady()
        }
        if (playback.isPlaying()) {
            schedulePlayback()
        }
        notifyListeners()
    }

    override fun seekToTimestampNs(timestampNs: Long) {
        val idx = cache.nearestIndexByTimestamp(timestampNs)
        if (idx >= 0) seek(idx)
    }

    override fun seekToRoutePositionMm(routeMm: Float) {
        val idx = cache.nearestIndexByRouteMm(routeMm)
        if (idx >= 0) seek(idx)
    }

    override fun seekToEventId(eventId: Long) {
        val idx = cache.indexOfEventId(eventId)
        if (idx >= 0) seek(idx)
    }

    override fun nextEvent() {
        if (cache.isEmpty()) return
        val next = (timeline.index() + 1).coerceAtMost(cache.frameCount() - 1)
        seek(next)
        if (timeline.index() >= cache.frameCount() - 1) {
            playback.stop()
            stateMachine.toCompleted()
            notifyListeners()
        }
    }

    override fun previousEvent() {
        if (cache.isEmpty()) return
        seek((timeline.index() - 1).coerceAtLeast(0))
    }

    override fun setPlaybackSpeed(speed: Float) {
        playback.setPlaybackSpeed(speed)
        if (playback.isPlaying()) schedulePlayback()
        notifyListeners()
    }

    override fun playbackSpeed(): Float = playback.playbackSpeed()

    override fun currentEvent(): ReplayFrame? = timeline.currentFrame()

    override fun currentSession(): InspectionSessionSummary? = cache.summary()

    override fun currentState(): ReplayPlaybackState = stateMachine.state()

    override fun currentTimelinePosition(): ReplayPosition = timeline.position()

    override fun currentRoutePositionMm(): Float =
        timeline.currentFrame()?.routePositionMm ?: 0f

    override fun currentStatistics(): ReplayEngineStatistics =
        statisticsProvider.statistics(
            summary = cache.summary(),
            frames = cache.frames(),
            current = timeline.currentFrame(),
            currentIndex = timeline.index(),
        )

    override fun events(): List<ReplayFrame> = cache.frames()

    override fun frameCount(): Int = cache.frameCount()

    override fun currentIndex(): Int = timeline.index()

    override fun sessionId(): String? = cache.sessionId()

    override fun errorMessage(): String? = lastError

    override fun addListener(listener: ReplayEngine.Listener) {
        synchronized(listeners) { listeners.add(listener) }
    }

    override fun removeListener(listener: ReplayEngine.Listener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    override fun clear() {
        playback.clear()
        cache.clear()
        timeline.clear()
        stateMachine.toIdle()
        lastError = null
        notifyListeners()
    }

    private fun advanceFromPlayback() {
        if (!playback.isPlaying()) return
        if (cache.isEmpty()) {
            playback.stop()
            return
        }
        val cur = timeline.index()
        if (cur >= cache.frameCount() - 1) {
            playback.stop()
            stateMachine.toCompleted()
            notifyListeners()
            return
        }
        timeline.seek(cache.frames(), cur + 1)
        notifyListeners()
        schedulePlayback()
    }

    private fun schedulePlayback() {
        val frames = cache.frames()
        val cur = timeline.currentFrame()
        val next = frames.getOrNull(timeline.index() + 1)
        playback.scheduleNext(playback.delayUntilNext(cur, next))
    }

    private fun notifyListeners() {
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it.onReplayChanged() }
    }
}
