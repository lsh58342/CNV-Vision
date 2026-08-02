package com.example.cnv.rule

/**
 * Configurable Rule definition managed by [RuleDefinitionRepository] (STEP 18).
 * Not evaluated until [InspectionRuleEngine] runs against Analysis Result.
 */
data class RuleDefinition(
    val ruleId: String,
    val version: Int,
    val category: RuleCategory,
    val scope: RuleScope = RuleScope.GLOBAL,
    val enabled: Boolean = true,
    val priority: Int = DEFAULT_PRIORITY,
    val severity: RuleSeverity,
    val recommendation: RuleRecommendation,
    val description: String,
    val metric: RuleMetric,
    val compareOp: RuleCompareOp,
    val threshold: Float,
    val tolerance: Float = 0f,
) {
    companion object {
        const val DEFAULT_PRIORITY = 100
    }
}

/**
 * Context for scope resolution during evaluation.
 */
data class RuleEvaluationContext(
    val factoryId: String? = null,
    val buildingId: String? = null,
    val floorId: String? = null,
    val drawingId: String? = null,
    val zoneId: String? = null,
    val ruleCatalogVersionSnapshot: Int = 0,
)
