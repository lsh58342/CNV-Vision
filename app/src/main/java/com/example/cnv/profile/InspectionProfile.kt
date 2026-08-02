package com.example.cnv.profile

import com.example.cnv.core.config.IMUConfig
import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.factory.model.ConveyorProfileSnapshot
import com.example.cnv.replay.analysis.ReplayAnalysisConfig
import com.example.cnv.rule.RuleSeverity

/**
 * Drawing-scoped Sensor Profile (STEP 19-2).
 * Stored for Inspection Snapshot / Excel — does not modify IMU algorithms.
 */
data class SensorProfile(
    val gravityFilterAlpha: Float = IMUConfig.DEFAULT_LOW_PASS_ALPHA,
    val highPassAlpha: Float = IMUConfig.DEFAULT_HIGH_PASS_ALPHA,
    val minimumShockThreshold: Float = IMUConfig.DEFAULT_SHOCK_ACCEL_THRESHOLD,
    val peakIntervalNs: Long = IMUConfig.DEFAULT_PEAK_DURATION_NS,
    val movingAverageWindow: Int = DEFAULT_MOVING_AVERAGE_WINDOW,
    val trackingConfidenceThreshold: Float = ReplayAnalysisConfig.DEFAULT_LOW_CONFIDENCE_THRESHOLD,
) {
    companion object {
        const val DEFAULT_MOVING_AVERAGE_WINDOW = 5
        val DEFAULT = SensorProfile()
    }
}

/**
 * Per-rule override entry for Rule Profile (STEP 19-2).
 */
data class RuleProfileEntry(
    val ruleId: String,
    val enabled: Boolean = true,
    val ruleVersion: Int = 1,
    val thresholdOverride: Float? = null,
    val severityOverride: RuleSeverity? = null,
)

data class RuleProfile(
    val catalogVersion: Int = 1,
    val entries: List<RuleProfileEntry> = emptyList(),
) {
    companion object {
        val DEFAULT = RuleProfile()
    }
}

data class ExportProfile(
    val excelVersion: Int = DEFAULT_EXCEL_VERSION,
    val exportOption: String = DEFAULT_EXPORT_OPTION,
    val timeFormat: String = DEFAULT_TIME_FORMAT,
    val coordinateFormat: String = DEFAULT_COORDINATE_FORMAT,
) {
    companion object {
        const val DEFAULT_EXCEL_VERSION = 2
        const val DEFAULT_EXPORT_OPTION = "FULL"
        const val DEFAULT_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        const val DEFAULT_COORDINATE_FORMAT = "DRAWING_XY"
        val DEFAULT = ExportProfile()
    }
}

/**
 * Full Inspection Profile for a Drawing (STEP 19-2).
 */
data class InspectionProfile(
    val conveyor: ConveyorProfile = ConveyorProfile.fromConfig(),
    val sensor: SensorProfile = SensorProfile.DEFAULT,
    val rule: RuleProfile = RuleProfile.DEFAULT,
    val export: ExportProfile = ExportProfile.DEFAULT,
    val updatedAtMs: Long = 0L,
) {
    companion object {
        fun default(conveyor: ConveyorProfile = ConveyorProfile.fromConfig()) =
            InspectionProfile(conveyor = conveyor)
    }
}

/**
 * Frozen Inspection Profile at Session start (STEP 19-2).
 */
data class InspectionProfileSnapshot(
    val conveyor: ConveyorProfileSnapshot = ConveyorProfileSnapshot.empty(),
    val sensor: SensorProfile = SensorProfile.DEFAULT,
    val rule: RuleProfile = RuleProfile.DEFAULT,
    val export: ExportProfile = ExportProfile.DEFAULT,
    val capturedAtMs: Long = System.currentTimeMillis(),
) {
    companion object {
        fun from(profile: InspectionProfile) = InspectionProfileSnapshot(
            conveyor = ConveyorProfileSnapshot.from(profile.conveyor),
            sensor = profile.sensor,
            rule = profile.rule,
            export = profile.export,
            capturedAtMs = System.currentTimeMillis(),
        )

        fun empty() = InspectionProfileSnapshot()
    }
}
