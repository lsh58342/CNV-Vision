package com.example.cnv.route

import com.example.cnv.map.RouteRepository

/**
 * Converts [RouteCandidate] list into [com.example.cnv.map.Route], stores via [RouteRepository].
 * Does not reference DWGImporter. Only this class may call [RouteRepository.setRoute] in this STEP.
 */
class RouteGenerator(
    private val routeRepository: RouteRepository,
    private val routeConfig: RouteConfig = RouteConfig.DEFAULT,
    private val coordinateConfig: CoordinateConfig = CoordinateConfig.DEFAULT,
    private val builder: RouteBuilder = RouteBuilder(routeConfig, coordinateConfig),
    private val optimizer: RouteOptimizer = RouteOptimizer(routeConfig),
) {

    @Volatile
    private var latest: RouteImportResult? = null

    fun latestResult(): RouteImportResult? = latest

    /**
     * @return null when candidates cannot form a valid route after normalization.
     */
    fun generate(
        candidates: List<RouteCandidate>,
        routeName: String = DEFAULT_ROUTE_NAME,
    ): RouteImportResult? {
        if (candidates.isEmpty()) return null
        val draft = builder.build(candidates, routeName)
        val optimized = optimizer.normalize(draft) ?: return null
        val mapper = CoordinateMapper(
            segmentGeometry = optimized.segmentGeometry,
            config = coordinateConfig,
        )
        routeRepository.setRoute(optimized.route)
        val result = RouteImportResult(
            route = optimized.route,
            routeName = optimized.route.name,
            nodeCount = optimized.route.nodes.size,
            segmentCount = optimized.route.segments.size,
            branchCount = optimized.branchCount,
            totalRouteLengthMm = optimized.totalLengthMm,
            coordinateScale = coordinateConfig.coordinateScale,
            coordinateOffsetX = coordinateConfig.offsetX,
            coordinateOffsetY = coordinateConfig.offsetY,
            mapper = mapper,
        )
        latest = result
        return result
    }

    companion object {
        const val DEFAULT_ROUTE_NAME = "Generated Conveyor Route"
    }
}
