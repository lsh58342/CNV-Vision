package com.example.cnv.rule

/**
 * Seeds default Global Rule definitions into [RuleDefinitionRepository] (STEP 18).
 * Future Import/Export / Factory overrides call [RuleDefinitionRepository.upsert].
 */
object DefaultRuleCatalog {

    fun seedInto(repository: RuleDefinitionRepository) {
        repository.seed(defaults())
    }

    fun defaults(): List<RuleDefinition> = listOf(
        RuleDefinition(
            ruleId = RuleIds.SHOCK_001,
            version = 1,
            category = RuleCategory.SHOCK,
            severity = RuleSeverity.HIGH,
            recommendation = RuleRecommendation.INSPECT_ROLLER,
            description = "High Shock",
            metric = RuleMetric.MAX_SHOCK,
            compareOp = RuleCompareOp.GTE,
            threshold = RuleConfigDefaults.HIGH_SHOCK_STRENGTH,
            tolerance = RuleConfigDefaults.ZERO_TOLERANCE,
            priority = 10,
        ),
        RuleDefinition(
            ruleId = RuleIds.SHOCK_002,
            version = 1,
            category = RuleCategory.SHOCK,
            severity = RuleSeverity.MEDIUM,
            recommendation = RuleRecommendation.INSPECT_BEARING,
            description = "Elevated Shock Count",
            metric = RuleMetric.SHOCK_COUNT,
            compareOp = RuleCompareOp.GTE,
            threshold = RuleConfigDefaults.HIGH_SHOCK_COUNT.toFloat(),
            priority = 20,
        ),
        RuleDefinition(
            ruleId = RuleIds.TRACK_001,
            version = 1,
            category = RuleCategory.TRACKING,
            severity = RuleSeverity.HIGH,
            recommendation = RuleRecommendation.CHECK_CAMERA_POSITION,
            description = "Tracking Loss",
            metric = RuleMetric.TRACKING_LOSS,
            compareOp = RuleCompareOp.GTE,
            threshold = RuleConfigDefaults.TRACKING_LOSS_MIN,
            priority = 30,
        ),
        RuleDefinition(
            ruleId = RuleIds.TRACK_002,
            version = 1,
            category = RuleCategory.TRACKING,
            severity = RuleSeverity.MEDIUM,
            recommendation = RuleRecommendation.CHECK_CAMERA_POSITION,
            description = "Low Confidence Frames",
            metric = RuleMetric.LOW_CONFIDENCE_COUNT,
            compareOp = RuleCompareOp.GTE,
            threshold = RuleConfigDefaults.LOW_CONFIDENCE_COUNT,
            priority = 40,
        ),
        RuleDefinition(
            ruleId = RuleIds.SPEED_001,
            version = 1,
            category = RuleCategory.SPEED,
            severity = RuleSeverity.HIGH,
            recommendation = RuleRecommendation.CHECK_CONVEYOR_SPEED,
            description = "Speed Mismatch",
            metric = RuleMetric.NOMINAL_SPEED_DIFF_MM_PER_SEC,
            compareOp = RuleCompareOp.GTE,
            threshold = RuleConfigDefaults.SPEED_MISMATCH_MM_PER_SEC,
            priority = 50,
        ),
        RuleDefinition(
            ruleId = RuleIds.COVERAGE_001,
            version = 1,
            category = RuleCategory.COVERAGE,
            severity = RuleSeverity.MEDIUM,
            recommendation = RuleRecommendation.RE_RUN_INSPECTION,
            description = "Low Coverage",
            metric = RuleMetric.ROUTE_COVERAGE,
            compareOp = RuleCompareOp.LTE,
            threshold = RuleConfigDefaults.LOW_ROUTE_COVERAGE,
            priority = 60,
        ),
        RuleDefinition(
            ruleId = RuleIds.VALIDATION_001,
            version = 1,
            category = RuleCategory.VALIDATION,
            severity = RuleSeverity.MEDIUM,
            recommendation = RuleRecommendation.MANUAL_VERIFICATION,
            description = "Validation Warning",
            metric = RuleMetric.VALIDATION_SCORE,
            compareOp = RuleCompareOp.LTE,
            threshold = RuleConfigDefaults.VALIDATION_WARN_SCORE,
            priority = 70,
        ),
        RuleDefinition(
            ruleId = RuleIds.ZONE_001,
            version = 1,
            category = RuleCategory.ZONE,
            severity = RuleSeverity.HIGH,
            recommendation = RuleRecommendation.INSPECT_CONVEYOR_JOINT,
            description = "Zone High Shock",
            metric = RuleMetric.ZONE_SHOCK_COUNT,
            compareOp = RuleCompareOp.GTE,
            threshold = RuleConfigDefaults.ZONE_HIGH_SHOCK_COUNT,
            priority = 80,
        ),
        RuleDefinition(
            ruleId = RuleIds.SESSION_001,
            version = 1,
            category = RuleCategory.SESSION,
            severity = RuleSeverity.LOW,
            recommendation = RuleRecommendation.RE_RUN_INSPECTION,
            description = "Incomplete Session",
            metric = RuleMetric.SESSION_COMPLETENESS,
            compareOp = RuleCompareOp.LTE,
            threshold = RuleConfigDefaults.SESSION_MIN_COMPLETENESS,
            priority = 90,
        ),
    )
}
