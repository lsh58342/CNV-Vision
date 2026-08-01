package com.example.cnv.fusion

import com.example.cnv.core.event.CalibrationEvent
import com.example.cnv.core.event.CoreEventModule
import com.example.cnv.core.event.DistanceEvent
import com.example.cnv.core.event.EventDispatcher
import com.example.cnv.core.event.ShockEvent

/**
 * Lifecycle owner for Sensor Fusion.
 * Subscribes to Distance/Shock/Calibration events only — never Camera or IMU APIs.
 */
class FusionEngine(
    private val config: FusionConfig = FusionConfig.DEFAULT,
    private val eventDispatcher: EventDispatcher = CoreEventModule.eventDispatcher(),
    val repository: FusionRepository = FusionRepository(config.historyCapacity),
    initialCalibrated: Boolean = false,
) {

    private val ruleEngine = FusionRuleEngine(config)
    private val processor = FusionProcessor(
        ruleEngine = ruleEngine,
        repository = repository,
        config = config,
        onFused = { result ->
            eventDispatcher.dispatch(result.toFusionEvent())
        },
        initialCalibrated = initialCalibrated,
    )

    private val onDistance: (DistanceEvent) -> Unit = { processor.onDistance(it) }
    private val onShock: (ShockEvent) -> Unit = { processor.onShock(it) }
    private val onCalibration: (CalibrationEvent) -> Unit = { processor.onCalibration(it) }

    @Volatile
    private var running: Boolean = false

    fun start() {
        if (running) return
        running = true
        eventDispatcher.subscribe(DistanceEvent::class.java, onDistance)
        eventDispatcher.subscribe(ShockEvent::class.java, onShock)
        eventDispatcher.subscribe(CalibrationEvent::class.java, onCalibration)
    }

    fun stop() {
        if (!running) return
        running = false
        eventDispatcher.unsubscribe(DistanceEvent::class.java, onDistance)
        eventDispatcher.unsubscribe(ShockEvent::class.java, onShock)
        eventDispatcher.unsubscribe(CalibrationEvent::class.java, onCalibration)
        processor.clear()
    }
}
