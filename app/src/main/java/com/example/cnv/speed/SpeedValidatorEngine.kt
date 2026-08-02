package com.example.cnv.speed

import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.DistanceEvent
import com.example.cnv.core.event.EventDispatcher
import com.example.cnv.core.event.FusionEvent
import com.example.cnv.core.event.SpeedValidationEvent
import com.example.cnv.factory.model.ConveyorProfile

/**
 * Lifecycle owner for Speed Validation (STEP 15-2).
 * Subscribes to DistanceEvent (+ latest Fusion confidence) — does not modify Fusion/OpenCV/Camera.
 *
 * Architecture: Conveyor Profile → Speed Validator → (validated) Fusion Confidence → Summary
 */
class SpeedValidatorEngine(
    private val config: SpeedValidationConfig = SpeedValidationConfig.DEFAULT,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
    private val profileProvider: () -> ConveyorProfile?,
) {

    private val validator = SpeedValidator(config)
    private val lock = Any()

    @Volatile
    private var running = false

    /** Profile frozen at Inspection start — mid-session Drawing edits do not apply. */
    @Volatile
    private var sessionProfile: ConveyorProfile? = null

    @Volatile
    private var latestFusionConfidence: Float? = null

    @Volatile
    private var latestSample: SpeedValidationSample? = null

    @Volatile
    private var speedMismatchWarning = false

    private var consecutiveOverTolerance = 0
    private var sumExpectedSpeed = 0.0
    private var sumMeasuredSpeed = 0.0
    private var sumDifference = 0.0
    private var sumConfidence = 0.0
    private var maxDifference = 0f
    private var sampleCount = 0
    private var outlierCount = 0
    private var mismatchEver = false

    private val onDistance: (DistanceEvent) -> Unit = { event ->
        processDistance(event)
    }

    private val onFusion: (FusionEvent) -> Unit = { event ->
        latestFusionConfidence = event.confidence
    }

    fun start() {
        if (running) return
        running = true
        eventDispatcher.subscribe(DistanceEvent::class.java, onDistance)
        eventDispatcher.subscribe(FusionEvent::class.java, onFusion)
    }

    fun stop() {
        if (!running) return
        running = false
        eventDispatcher.unsubscribe(DistanceEvent::class.java, onDistance)
        eventDispatcher.unsubscribe(FusionEvent::class.java, onFusion)
    }

    /** Freeze Conveyor Profile for this Inspection Session. */
    fun beginSession(profile: ConveyorProfile?) {
        resetSession()
        sessionProfile = profile
    }

    fun endSession(): SpeedValidationSummary {
        val summary = sessionSummary()
        sessionProfile = null
        return summary
    }

    fun resetSession() {
        synchronized(lock) {
            consecutiveOverTolerance = 0
            sumExpectedSpeed = 0.0
            sumMeasuredSpeed = 0.0
            sumDifference = 0.0
            sumConfidence = 0.0
            maxDifference = 0f
            sampleCount = 0
            outlierCount = 0
            mismatchEver = false
            speedMismatchWarning = false
            latestSample = null
        }
    }

    fun latest(): SpeedValidationSample? = latestSample

    fun mismatchWarning(): Boolean = speedMismatchWarning

    fun sessionSummary(): SpeedValidationSummary {
        synchronized(lock) {
            if (sampleCount == 0) return SpeedValidationSummary.EMPTY
            val n = sampleCount.toFloat()
            return SpeedValidationSummary(
                sampleCount = sampleCount,
                averageExpectedSpeedMPerMin = (sumExpectedSpeed / n).toFloat(),
                averageMeasuredSpeedMPerMin = (sumMeasuredSpeed / n).toFloat(),
                maximumDifferenceMm = maxDifference,
                averageDifferenceMm = (sumDifference / n).toFloat(),
                validationScore = (sumConfidence / n).toFloat(),
                outlierCount = outlierCount,
                mismatchWarningTriggered = mismatchEver,
            )
        }
    }

    private fun activeProfile(): ConveyorProfile? = sessionProfile ?: profileProvider()

    private fun processDistance(event: DistanceEvent) {
        val profile = activeProfile() ?: return
        val sample = validator.validate(
            measuredDistanceMm = event.distanceMm,
            profile = profile,
            timestampNs = event.timestampNs,
            fusionConfidence = latestFusionConfidence,
        ) ?: return
        onSample(sample)
    }

    private fun onSample(sample: SpeedValidationSample) {
        val warning: Boolean
        synchronized(lock) {
            latestSample = sample
            sampleCount += 1
            sumExpectedSpeed += sample.expectedSpeedMPerMin
            sumMeasuredSpeed += sample.measuredSpeedMPerMin
            sumDifference += sample.differenceMm
            sumConfidence += sample.confidence
            if (sample.differenceMm > maxDifference) maxDifference = sample.differenceMm
            if (sample.outlier) outlierCount += 1

            if (sample.withinTolerance) {
                consecutiveOverTolerance = 0
            } else {
                consecutiveOverTolerance += 1
                if (consecutiveOverTolerance >= config.continuousMismatchThreshold) {
                    speedMismatchWarning = true
                    mismatchEver = true
                }
            }
            warning = speedMismatchWarning
        }

        val validatedFusion = sample.validatedFusionConfidence ?: sample.confidence
        eventDispatcher.dispatch(
            SpeedValidationEvent(
                timestampNs = sample.timestampNs,
                nominalSpeedMPerMin = sample.nominalSpeedMPerMin,
                expectedDistanceMm = sample.expectedDistanceMm,
                measuredDistanceMm = sample.measuredDistanceMm,
                differenceMm = sample.differenceMm,
                confidence = sample.confidence,
                outlier = sample.outlier,
                withinTolerance = sample.withinTolerance,
                speedMismatchWarning = warning,
                validatedFusionConfidence = validatedFusion,
            ),
        )
    }

    companion object {
        @Volatile
        private var shared: SpeedValidatorEngine? = null

        fun bindShared(engine: SpeedValidatorEngine) {
            shared = engine
        }

        fun sharedOrNull(): SpeedValidatorEngine? = shared
    }
}

