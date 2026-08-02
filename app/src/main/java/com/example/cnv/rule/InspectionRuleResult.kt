package com.example.cnv.rule

/**
 * Session-level Rule Engine output (STEP 18).
 * Cached by [InspectionRuleRepository]; Review / Replay / Report share this.
 */
data class InspectionRuleResult(
    val sessionId: String,
    val drawingId: String,
    val ruleCatalogVersion: Int = 0,
    val evaluatedAtMs: Long = System.currentTimeMillis(),
    val hits: List<RuleHit> = emptyList(),
    val warnings: List<InspectionWarning> = emptyList(),
    val issues: List<InspectionIssue> = emptyList(),
    val zoneSummaries: List<InspectionRuleZoneSummary> = emptyList(),
) {
    fun triggered(): List<RuleHit> = hits.filter { it.triggered }

    companion object {
        fun empty(sessionId: String = "", drawingId: String = "") = InspectionRuleResult(
            sessionId = sessionId,
            drawingId = drawingId,
        )
    }
}

/**
 * Warning Summary row derived from triggered [RuleHit]s.
 */
data class InspectionWarning(
    val ruleId: String,
    val severity: RuleSeverity,
    val label: String,
    val detail: String = "",
    val recommendation: RuleRecommendation = RuleRecommendation.MANUAL_VERIFICATION,
    val category: RuleCategory = RuleCategory.SESSION,
)

/**
 * Zone Issue List row (zones with triggered Zone / related rules).
 */
data class InspectionIssue(
    val zoneId: String,
    val zoneName: String,
    val ruleId: String,
    val severity: RuleSeverity,
    val occurrenceCount: Int,
    val recommendation: RuleRecommendation = RuleRecommendation.MANUAL_VERIFICATION,
    val issueType: String = "",
)

data class InspectionRuleZoneSummary(
    val zoneId: String,
    val zoneName: String,
    val distanceMm: Float,
    val shockCount: Int,
    val coverage: Float,
    val validationScore: Float,
)
