package com.example.cnv.rule

/**
 * Single Rule evaluation record (STEP 18 Rule Result row).
 * Review / Replay / Report consume these — they do not re-evaluate.
 */
data class RuleHit(
    val ruleId: String,
    val ruleVersion: Int,
    val severity: RuleSeverity,
    val triggered: Boolean,
    val description: String,
    val recommendation: RuleRecommendation,
    val relatedBuildingId: String? = null,
    val relatedFloorId: String? = null,
    val relatedDrawingId: String? = null,
    val relatedZoneId: String? = null,
    val relatedZoneName: String? = null,
    val metricValue: Float = 0f,
    val timestampMs: Long = System.currentTimeMillis(),
    val category: RuleCategory = RuleCategory.SESSION,
)
