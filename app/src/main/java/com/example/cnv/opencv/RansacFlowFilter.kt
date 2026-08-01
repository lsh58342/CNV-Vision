package com.example.cnv.opencv

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Translation-model RANSAC over optical-flow pairs.
 */
class RansacFlowFilter(
    private val maxIterations: Int = MAX_ITERS,
    private val residualThresholdPx: Double = REPROJ_THRESHOLD_PX,
    private val random: Random = Random.Default,
) {

    data class FilterResult(
        val inliers: List<FlowPair>,
        val outliers: List<FlowPair>,
        val consensusTx: Double,
        val consensusTy: Double,
        val confidence: Float,
    )

    fun filter(pairs: List<FlowPair>): FilterResult {
        if (pairs.isEmpty()) {
            return FilterResult(
                inliers = emptyList(),
                outliers = emptyList(),
                consensusTx = 0.0,
                consensusTy = 0.0,
                confidence = 0f,
            )
        }

        var bestInliers = emptyList<FlowPair>()
        var bestTx = 0.0
        var bestTy = 0.0

        val iterationCount = minOf(maxIterations, pairs.size * 4).coerceAtLeast(1)
        repeat(iterationCount) {
            val sample = pairs[random.nextInt(pairs.size)]
            val tx = sample.to.x - sample.from.x
            val ty = sample.to.y - sample.from.y
            val inliers = pairs.filter { residual(it, tx, ty) <= residualThresholdPx }
            if (inliers.size > bestInliers.size) {
                bestInliers = inliers
                bestTx = tx
                bestTy = ty
            }
        }

        if (bestInliers.isNotEmpty()) {
            bestTx = bestInliers.map { it.to.x - it.from.x }.average()
            bestTy = bestInliers.map { it.to.y - it.from.y }.average()
            bestInliers = pairs.filter { residual(it, bestTx, bestTy) <= residualThresholdPx }
        }

        val inlierSet = bestInliers.toHashSet()
        val outliers = pairs.filter { it !in inlierSet }

        val ratio = bestInliers.size.toFloat() / pairs.size.toFloat()
        val confidence = (
            ratio * (bestInliers.size.toFloat() / MIN_INLIERS_FOR_FULL_CONF)
            ).coerceIn(0f, 1f)

        return FilterResult(
            inliers = bestInliers,
            outliers = outliers,
            consensusTx = bestTx,
            consensusTy = bestTy,
            confidence = confidence,
        )
    }

    private fun residual(pair: FlowPair, tx: Double, ty: Double): Double {
        val dx = (pair.to.x - pair.from.x) - tx
        val dy = (pair.to.y - pair.from.y) - ty
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val MAX_ITERS = 100
        const val REPROJ_THRESHOLD_PX = 3.0
        const val MIN_INLIERS_FOR_FULL_CONF = 40
    }
}
