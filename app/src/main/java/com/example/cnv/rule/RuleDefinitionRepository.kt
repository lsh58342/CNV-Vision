package com.example.cnv.rule

/**
 * Stores Rule definitions (STEP 18).
 * Rules are not hardcoded in the Engine — Engine reads from this repository.
 * Scope overrides: more specific [RuleScopeLevel] replaces broader definitions for the same ruleId.
 */
class RuleDefinitionRepository {

    private val lock = Any()
    private val definitions = ArrayList<RuleDefinition>()
    private var catalogVersion: Int = INITIAL_CATALOG_VERSION

    fun catalogVersion(): Int = synchronized(lock) { catalogVersion }

    fun all(): List<RuleDefinition> = synchronized(lock) { definitions.toList() }

    fun upsert(definition: RuleDefinition) {
        synchronized(lock) {
            val idx = definitions.indexOfFirst {
                it.ruleId == definition.ruleId &&
                    it.scope.level == definition.scope.level &&
                    it.scope.targetId == definition.scope.targetId
            }
            if (idx >= 0) {
                definitions[idx] = definition
            } else {
                definitions.add(definition)
            }
            catalogVersion += 1
        }
    }

    fun seed(seed: List<RuleDefinition>, reset: Boolean = true) {
        synchronized(lock) {
            if (reset) definitions.clear()
            definitions.addAll(seed)
            if (reset) {
                catalogVersion = INITIAL_CATALOG_VERSION
            } else {
                catalogVersion += 1
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            definitions.clear()
            catalogVersion = INITIAL_CATALOG_VERSION
        }
    }

    /**
     * Resolve effective definitions for [context]: one definition per ruleId,
     * preferring the most specific matching scope. Disabled rules are omitted.
     * Ordered by priority ascending (lower = earlier).
     */
    fun resolveEffective(context: RuleEvaluationContext): List<RuleDefinition> {
        val all = all()
        val byId = LinkedHashMap<String, RuleDefinition>()
        val candidates = all.filter { it.enabled && scopeMatches(it.scope, context) }
            .sortedWith(
                compareBy<RuleDefinition> { it.scope.level.ordinal }
                    .thenBy { it.priority },
            )
        for (def in candidates) {
            // Later (more specific) overwrites earlier.
            byId[def.ruleId] = def
        }
        return byId.values.sortedBy { it.priority }
    }

    private fun scopeMatches(scope: RuleScope, ctx: RuleEvaluationContext): Boolean {
        return when (scope.level) {
            RuleScopeLevel.GLOBAL -> true
            RuleScopeLevel.FACTORY ->
                scope.targetId != null && scope.targetId == ctx.factoryId
            RuleScopeLevel.BUILDING ->
                scope.targetId != null && scope.targetId == ctx.buildingId
            RuleScopeLevel.FLOOR ->
                scope.targetId != null && scope.targetId == ctx.floorId
            RuleScopeLevel.DRAWING ->
                scope.targetId != null && scope.targetId == ctx.drawingId
            RuleScopeLevel.ZONE ->
                scope.targetId != null && scope.targetId == ctx.zoneId
        }
    }

    companion object {
        const val INITIAL_CATALOG_VERSION = 1
    }
}
