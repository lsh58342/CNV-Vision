package com.example.cnv.position

import android.content.Context
import com.example.cnv.config.CalibrationManager
import com.example.cnv.vio.CameraMountCalibration
import com.example.cnv.vio.VisualInertialConfig

/**
 * Factory for Inspection / OpenCV wiring. Default = Visual-Inertial.
 */
object PositionProviderFactory {

    @Volatile
    private var mode: PositionProviderMode = PositionProviderMode.VISUAL_INERTIAL

    fun setMode(mode: PositionProviderMode) {
        this.mode = mode
        println("LOG[VIO] PositionProvider mode=$mode")
    }

    fun currentMode(): PositionProviderMode = mode

    fun create(
        context: Context,
        calibrationManager: CalibrationManager = CalibrationManager.getInstance(context),
        config: VisualInertialConfig = VisualInertialConfig.DEFAULT,
        mount: CameraMountCalibration = CameraMountCalibration.IDENTITY,
    ): PositionProvider {
        return when (mode) {
            PositionProviderMode.VISUAL_INERTIAL -> VisualInertialPositionProvider(
                context = context,
                config = config,
                mount = mount,
            )
            PositionProviderMode.LEGACY_VISION -> LegacyVisionPositionProvider(calibrationManager)
        }
    }
}
