package com.example.cnv.route

import com.example.cnv.map.Route

/**
 * Single validation check. Implementations never mutate [Route].
 */
fun interface ValidationRule {
    fun validate(route: Route, config: ValidationConfig): List<ValidationIssue>
}
