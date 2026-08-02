package com.example.cnv.factory.model

/**
 * Conveyor motion profile for a Drawing (STEP 15-1).
 * Metadata only — not used for distance / fusion correction.
 */
enum class ConveyorMotionProfile {
    CONSTANT,
    VARIABLE,
    UNKNOWN,
}

/**
 * Conveyor travel direction for a Drawing (STEP 15-1).
 */
enum class ConveyorDirection {
    FORWARD,
    REVERSE,
}

/**
 * Drawing-scoped conveyor metadata.
 * [nominalSpeedMPerMin] has no hardcoded default — user sets per Drawing.
 */
data class ConveyorProfile(
    val nominalSpeedMPerMin: Float? = null,
    val speedTolerancePercent: Float,
    val direction: ConveyorDirection,
    val expectedFps: Float,
    val motionProfile: ConveyorMotionProfile,
    val lastUpdatedMs: Long = 0L,
) {
    val isNominalSpeedSet: Boolean get() = nominalSpeedMPerMin != null

    companion object {
        /** New Drawing profile using Config defaults (no nominal speed). */
        fun fromConfig(config: ConveyorProfileConfig = ConveyorProfileConfig.DEFAULT): ConveyorProfile =
            ConveyorProfile(
                nominalSpeedMPerMin = null,
                speedTolerancePercent = config.defaultSpeedTolerancePercent,
                direction = config.defaultDirection,
                expectedFps = config.defaultExpectedFps,
                motionProfile = config.defaultMotionProfile,
                lastUpdatedMs = 0L,
            )
    }
}

/**
 * Config for Conveyor Profile defaults (no magic numbers at call sites).
 * Nominal speed is intentionally absent — user-defined per Drawing.
 */
data class ConveyorProfileConfig(
    val defaultExpectedFps: Float = DEFAULT_EXPECTED_FPS,
    val defaultSpeedTolerancePercent: Float = DEFAULT_SPEED_TOLERANCE_PERCENT,
    val defaultDirection: ConveyorDirection = ConveyorDirection.FORWARD,
    val defaultMotionProfile: ConveyorMotionProfile = ConveyorMotionProfile.CONSTANT,
) {
    companion object {
        val DEFAULT = ConveyorProfileConfig()
        const val DEFAULT_EXPECTED_FPS = 30f
        const val DEFAULT_SPEED_TOLERANCE_PERCENT = 5f
    }
}

/**
 * Immutable snapshot of [ConveyorProfile] copied into an Inspection Session at start.
 */
data class ConveyorProfileSnapshot(
    val nominalSpeedMPerMin: Float?,
    val direction: ConveyorDirection,
    val expectedFps: Float,
    val motionProfile: ConveyorMotionProfile,
) {
    companion object {
        fun from(profile: ConveyorProfile) = ConveyorProfileSnapshot(
            nominalSpeedMPerMin = profile.nominalSpeedMPerMin,
            direction = profile.direction,
            expectedFps = profile.expectedFps,
            motionProfile = profile.motionProfile,
        )

        fun empty(config: ConveyorProfileConfig = ConveyorProfileConfig.DEFAULT) =
            ConveyorProfileSnapshot(
                nominalSpeedMPerMin = null,
                direction = config.defaultDirection,
                expectedFps = config.defaultExpectedFps,
                motionProfile = config.defaultMotionProfile,
            )
    }
}
