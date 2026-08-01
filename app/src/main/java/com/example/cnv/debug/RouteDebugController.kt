package com.example.cnv.debug

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.example.cnv.map.MapMatchingEngine
import com.example.cnv.map.RouteRepository
import com.example.cnv.route.CoordinateMapper
import com.example.cnv.route.RouteValidator
import com.example.cnv.route.ValidationIssue
import com.example.cnv.route.ValidationResult

/**
 * Wires read-only validation + route debug view. Does not mutate Route / MapMatching.
 */
class RouteDebugController(
    private val routeRepository: RouteRepository,
    private val routeDebugView: RouteDebugView,
    private val statsTextView: TextView,
    private val issuesTextView: TextView,
    private val mapMatchingEngine: MapMatchingEngine,
    private val validator: RouteValidator = RouteValidator(),
    private val mapperProvider: () -> CoordinateMapper?,
    private val refreshIntervalMs: Long = RouteDebugConfig.DEFAULT_STATS_REFRESH_MS,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var latestValidation: ValidationResult? = null
    private var selectedIssueIndex: Int = -1

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, refreshIntervalMs)
        }
    }

    init {
        routeDebugView.selectionListener = { nodeId, segmentId ->
            highlightMatchingIssue(nodeId, segmentId)
        }
    }

    fun start() {
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
    }

    fun stop() {
        handler.removeCallbacks(refreshRunnable)
    }

    fun latestValidation(): ValidationResult? = latestValidation

    fun selectIssue(index: Int) {
        val result = latestValidation ?: return
        if (index !in result.issues.indices) return
        selectedIssueIndex = index
        val issue = result.issues[index]
        routeDebugView.selectIssue(issue.nodeId, issue.segmentId)
        renderIssues(result.issues)
    }

    private fun refresh() {
        val result = validator.validate(routeRepository)
        latestValidation = result
        routeDebugView.setRouteData(
            route = routeRepository.current(),
            mapper = mapperProvider(),
            issues = result.issues,
            currentPosition = mapMatchingEngine.latestPosition(),
        )
        val stats = result.statistics
        statsTextView.text = buildString {
            append("Route Validation\n")
            append("Status: %s\n".format(result.severity.name))
            append("Errors: %d  Warnings: %d\n".format(result.errorCount, result.warningCount))
            append("Nodes: %d  Segs: %d  Branches: %d\n".format(stats.nodeCount, stats.segmentCount, stats.branchCount))
            append("Len tot/avg/min/max:\n")
            append(
                "%.1f / %.1f / %.1f / %.1f".format(
                    stats.totalRouteLengthMm,
                    stats.averageSegmentLengthMm,
                    stats.minimumSegmentLengthMm,
                    stats.maximumSegmentLengthMm,
                ),
            )
        }
        renderIssues(result.issues)
    }

    private fun renderIssues(issues: List<ValidationIssue>) {
        if (issues.isEmpty()) {
            issuesTextView.text = "Issues: none"
            return
        }
        issuesTextView.text = buildString {
            append("Issues (${issues.size})\n")
            issues.forEachIndexed { index, issue ->
                val marker = if (index == selectedIssueIndex) ">" else " "
                append(
                    "%s[%s] %s %s\n".format(
                        marker,
                        issue.severity.name.first(),
                        issue.type.name,
                        issue.message,
                    ),
                )
            }
        }
    }

    private fun highlightMatchingIssue(nodeId: String?, segmentId: String?) {
        val issues = latestValidation?.issues.orEmpty()
        selectedIssueIndex = issues.indexOfFirst {
            (nodeId != null && it.nodeId == nodeId) ||
                (segmentId != null && it.segmentId == segmentId)
        }
        renderIssues(issues)
    }
}
