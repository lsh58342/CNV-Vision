package com.example.cnv.inspection

import com.example.cnv.route.ValidationResult
import com.example.cnv.route.ValidationSeverity

/**
 * Maps an already-computed STEP 10-3 [ValidationResult] into a quality score.
 * Does not run RouteValidator.
 */
object RouteQualityScore {
    fun from(validation: ValidationResult?): Float {
        if (validation == null) return 0f
        val base = when (validation.severity) {
            ValidationSeverity.SUCCESS -> 1.0f
            ValidationSeverity.WARNING -> 0.7f
            ValidationSeverity.ERROR -> 0.35f
        }
        val penalty = validation.errorCount * 0.08f + validation.warningCount * 0.03f
        return (base - penalty).coerceIn(0f, 1f)
    }
}
