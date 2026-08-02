package com.example.cnv.rule

/**
 * Cached Rule Engine output for Inspection Review (STEP 17-1).
 * Review / Report consume this — they do not re-run rules.
 */
data class InspectionRuleResult(
    val sessionId: String,
    val drawingId: String,
    val evaluatedAtMs: Long = System.currentTimeMillis(),
    val warnings: List<InspectionWarning> = emptyList(),
    val issues: List<InspectionIssue> = emptyList(),
    val zoneSummaries: List<InspectionRuleZoneSummary> = emptyList(),
) {
    companion object {
        fun empty(sessionId: String = "", drawingId: String = "") = InspectionRuleResult(
            sessionId = sessionId,
            drawingId = drawingId,
        )
    }
}

data class InspectionWarning(
    val type: InspectionWarningType,
    val severity: InspectionRuleSeverity,
    val label: String,
    val detail: String = "",
)

/**
 * Zone-scoped issue for Review Issue List (zones with warnings only).
 */
data class InspectionIssue(
    val zoneId: String,
    val zoneName: String,
    val issueType: InspectionWarningType,
    val severity: InspectionRuleSeverity,
    val occurrenceCount: Int,
)

data class InspectionRuleZoneSummary(
    val zoneId: String,
    val zoneName: String,
    val distanceMm: Float,
    val shockCount: Int,
    val coverage: Float,
    val validationScore: Float,
)
