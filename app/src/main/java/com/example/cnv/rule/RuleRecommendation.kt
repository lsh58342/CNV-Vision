package com.example.cnv.rule

/**
 * Recommended operator action for a triggered Rule (STEP 18).
 */
enum class RuleRecommendation {
    INSPECT_ROLLER,
    INSPECT_BEARING,
    INSPECT_MOTOR,
    INSPECT_CONVEYOR_JOINT,
    CHECK_CAMERA_POSITION,
    CHECK_CONVEYOR_SPEED,
    RE_RUN_INSPECTION,
    MANUAL_VERIFICATION,
    ;

    fun displayLabel(): String = when (this) {
        INSPECT_ROLLER -> "Inspect Roller"
        INSPECT_BEARING -> "Inspect Bearing"
        INSPECT_MOTOR -> "Inspect Motor"
        INSPECT_CONVEYOR_JOINT -> "Inspect Conveyor Joint"
        CHECK_CAMERA_POSITION -> "Check Camera Position"
        CHECK_CONVEYOR_SPEED -> "Check Conveyor Speed"
        RE_RUN_INSPECTION -> "Re-run Inspection"
        MANUAL_VERIFICATION -> "Manual Verification"
    }
}
