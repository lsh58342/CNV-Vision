package com.example.cnv.ui.screen.inspection

/**
 * Inspection start gate — UI-facing blockers only (no engine changes).
 */
data class InspectionPreflight(
    val ok: Boolean,
    val blockers: List<String> = emptyList(),
) {
    companion object {
        fun ready() = InspectionPreflight(ok = true)

        fun blocked(vararg reasons: String) = InspectionPreflight(
            ok = false,
            blockers = reasons.filter { it.isNotBlank() },
        )
    }
}

data class InspectionStartResult(
    val started: Boolean,
    val preflight: InspectionPreflight,
)
