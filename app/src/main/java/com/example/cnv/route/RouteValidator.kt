package com.example.cnv.route

import com.example.cnv.map.Route
import com.example.cnv.map.RouteRepository

/**
 * Read-only route validation for Map Matching readiness.
 * Never mutates [RouteRepository] or [Route].
 */
class RouteValidator(
    private val config: ValidationConfig = ValidationConfig.DEFAULT,
    private val analyzer: RouteAnalyzer = RouteAnalyzer(),
    private val rules: List<ValidationRule> = DefaultValidationRules.all(),
) {

    fun validate(repository: RouteRepository): ValidationResult {
        return validate(repository.current())
    }

    fun validate(route: Route?): ValidationResult {
        if (route == null) {
            return ValidationResult.emptyMissing()
        }
        val issues = rules.flatMap { it.validate(route, config) }
        val statistics = analyzer.analyze(route)
        val severity = when {
            issues.any { it.severity == ValidationSeverity.ERROR } -> ValidationSeverity.ERROR
            issues.any { it.severity == ValidationSeverity.WARNING } -> ValidationSeverity.WARNING
            else -> ValidationSeverity.SUCCESS
        }
        return ValidationResult(
            severity = severity,
            issues = issues,
            statistics = statistics,
        )
    }
}
