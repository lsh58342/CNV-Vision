package com.example.cnv.report.excel

import com.example.cnv.analysis.InspectionAnalysisResult
import com.example.cnv.analysis.ZoneStatistics
import com.example.cnv.inspection.PersistedInspectionEvent
import com.example.cnv.rule.InspectionRuleResult
import com.example.cnv.rule.RuleRecommendation
import com.example.cnv.rule.RuleSeverity
import kotlin.math.sqrt

/**
 * Zone analytics for Excel export (STEP 19-2).
 * Aggregates stored Analysis / Rule / Event fields — does not run Analysis Engine.
 */
object ZoneExcelAnalytics {

    data class ShockStats(
        val maximum: Float = 0f,
        val average: Float = 0f,
        val minimum: Float = 0f,
        val median: Float = 0f,
        val p95: Float = 0f,
        val stdDev: Float = 0f,
        val densityPerMeter: Float = 0f,
        val shockCount: Int = 0,
    )

    data class ZoneRankRow(
        val rank: Int,
        val zoneId: String,
        val zoneName: String,
        val zoneLengthMm: Float,
        val shock: ShockStats,
        val coverage: Float,
        val validationScore: Float,
        val highestSeverity: RuleSeverity?,
        val triggeredRuleCount: Int,
        val recommendation: RuleRecommendation?,
        val priorityScore: Float,
    )

    fun buildRankings(
        analysis: InspectionAnalysisResult,
        rules: InspectionRuleResult,
        events: List<PersistedInspectionEvent>,
    ): List<ZoneRankRow> {
        val shocksByZone = assignShockSamples(events, analysis.zones)
        val zoneHits = rules.triggered()
            .filter { !it.relatedZoneId.isNullOrBlank() }
            .groupBy { it.relatedZoneId!! }
        val summaries = rules.zoneSummaries.associateBy { it.zoneId }
        val rows = analysis.zones.map { zone ->
            val samples = shocksByZone[zone.zoneId].orEmpty()
            val shock = computeShockStats(samples, zone.distanceMm, zone.shockCount)
            val hits = zoneHits[zone.zoneId].orEmpty()
            val highest = hits.minByOrNull { it.severity.ordinal }?.severity
            val recommendation = hits.minByOrNull { it.severity.ordinal }?.recommendation
            val validation = summaries[zone.zoneId]?.validationScore ?: analysis.validationScore
            val coverage = summaries[zone.zoneId]?.coverage ?: zone.coverage
            val priority = priorityScore(highest, shock, hits.size)
            ZoneRankRow(
                rank = 0,
                zoneId = zone.zoneId,
                zoneName = zone.zoneName,
                zoneLengthMm = zone.distanceMm,
                shock = shock,
                coverage = coverage,
                validationScore = validation,
                highestSeverity = highest,
                triggeredRuleCount = hits.size,
                recommendation = recommendation,
                priorityScore = priority,
            )
        }.sortedWith(
            compareByDescending<ZoneRankRow> { it.priorityScore }
                .thenBy { it.highestSeverity?.ordinal ?: RuleSeverity.INFO.ordinal }
                .thenByDescending { it.shock.maximum }
                .thenByDescending { it.triggeredRuleCount },
        )
        return rows.mapIndexed { index, row -> row.copy(rank = index + 1) }
    }

    fun topWorst(rankings: List<ZoneRankRow>, limit: Int = 10): List<ZoneRankRow> =
        rankings.take(limit)

    /**
     * Map events into zones using Analysis zone distance buckets (stored mm), not engine re-run.
     */
    fun assignShockSamples(
        events: List<PersistedInspectionEvent>,
        zones: List<ZoneStatistics>,
    ): Map<String, List<Float>> {
        if (zones.isEmpty()) return emptyMap()
        var cursor = 0f
        val ranges = zones.map { z ->
            val start = cursor
            cursor += z.distanceMm.coerceAtLeast(0f)
            Triple(z.zoneId, start, cursor)
        }
        val out = LinkedHashMap<String, ArrayList<Float>>()
        zones.forEach { out[it.zoneId] = ArrayList() }
        for (e in events) {
            if (!e.hasShock && e.shockStrength <= 0f) continue
            val d = e.distanceMm
            val zoneId = ranges.firstOrNull { d >= it.second && d <= it.third }?.first
                ?: ranges.lastOrNull()?.first
                ?: continue
            out.getOrPut(zoneId) { ArrayList() }.add(e.shockStrength)
        }
        return out
    }

    fun computeShockStats(
        samples: List<Float>,
        zoneLengthMm: Float,
        fallbackCount: Int,
    ): ShockStats {
        if (samples.isEmpty()) {
            val lengthM = (zoneLengthMm / 1000f).coerceAtLeast(0.001f)
            return ShockStats(
                shockCount = fallbackCount,
                densityPerMeter = fallbackCount / lengthM,
            )
        }
        val sorted = samples.sorted()
        val n = sorted.size
        val avg = sorted.sum() / n
        val variance = sorted.sumOf { ((it - avg) * (it - avg)).toDouble() } / n
        val lengthM = (zoneLengthMm / 1000f).coerceAtLeast(0.001f)
        return ShockStats(
            maximum = sorted.last(),
            average = avg,
            minimum = sorted.first(),
            median = percentile(sorted, 0.5f),
            p95 = percentile(sorted, 0.95f),
            stdDev = sqrt(variance).toFloat(),
            densityPerMeter = n / lengthM,
            shockCount = n,
        )
    }

    private fun percentile(sorted: List<Float>, p: Float): Float {
        if (sorted.isEmpty()) return 0f
        if (sorted.size == 1) return sorted[0]
        val idx = ((sorted.size - 1) * p).coerceIn(0f, (sorted.size - 1).toFloat())
        val lo = idx.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.lastIndex)
        val frac = idx - lo
        return sorted[lo] * (1f - frac) + sorted[hi] * frac
    }

    private fun priorityScore(
        severity: RuleSeverity?,
        shock: ShockStats,
        ruleCount: Int,
    ): Float {
        val sev = when (severity) {
            RuleSeverity.CRITICAL -> 1000f
            RuleSeverity.HIGH -> 700f
            RuleSeverity.MEDIUM -> 400f
            RuleSeverity.LOW -> 200f
            RuleSeverity.INFO -> 50f
            null -> 0f
        }
        return sev + shock.maximum * 10f + ruleCount * 25f + shock.densityPerMeter * 5f
    }
}
