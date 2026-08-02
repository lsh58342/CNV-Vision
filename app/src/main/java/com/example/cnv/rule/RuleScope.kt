package com.example.cnv.rule

/**
 * Rule applicability scope. More specific scopes override broader ones (STEP 18).
 */
enum class RuleScopeLevel {
    GLOBAL,
    FACTORY,
    BUILDING,
    FLOOR,
    DRAWING,
    ZONE,
}

data class RuleScope(
    val level: RuleScopeLevel = RuleScopeLevel.GLOBAL,
    /** Target id for non-GLOBAL scopes; null for GLOBAL. */
    val targetId: String? = null,
) {
    companion object {
        val GLOBAL = RuleScope(RuleScopeLevel.GLOBAL, null)
    }
}
