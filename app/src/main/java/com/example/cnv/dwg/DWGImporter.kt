package com.example.cnv.dwg

import com.example.cnv.route.RouteCandidate
import com.example.cnv.route.RouteExtractor

/**
 * Orchestrates DWG open → geometry parse → conveyor extraction → [RouteCandidate].
 * Does not touch MapMatchingEngine or RouteRepository.
 */
class DWGImporter(
    private val reader: DWGReader,
    private val config: DWGConfig = DWGConfig.DEFAULT,
    private val geometryExtractor: GeometryExtractor = GeometryExtractor(config),
    private val routeExtractor: RouteExtractor = RouteExtractor(config),
) {

    @Volatile
    private var latest: ImportResult? = null

    fun latestResult(): ImportResult? = latest

    /**
     * @param sourcePath path or logical key passed to [DWGReader.open]
     * @param layerName conveyor layer; defaults to [DWGConfig.layerFilter]
     */
    fun importFrom(
        sourcePath: String,
        layerName: String = config.layerFilter,
    ): ImportResult {
        val document = reader.open(sourcePath)
        val layers = reader.readLayers(document)
        val entities = reader.readEntities(document)
        val geometry = geometryExtractor.extract(
            fileName = document.fileName,
            layers = layers,
            entities = entities,
        )
        val extraction = routeExtractor.extract(geometry, layerName)
        val result = ImportResult(
            fileName = document.fileName,
            sourcePath = sourcePath,
            geometry = geometry,
            selectedLayer = extraction.selectedLayer,
            polylineCount = extraction.sourcePolylines.size,
            mergedPolylineCount = extraction.mergedPolylines.size,
            centerLineCount = extraction.centerLines.size,
            candidates = extraction.candidates,
        )
        latest = result
        return result
    }

    data class ImportResult(
        val fileName: String,
        val sourcePath: String,
        val geometry: GeometryModel,
        val selectedLayer: String,
        val polylineCount: Int,
        val mergedPolylineCount: Int,
        val centerLineCount: Int,
        val candidates: List<RouteCandidate>,
    ) {
        val layerNames: List<String> get() = geometry.layerNames()
        val routeCandidateCount: Int get() = candidates.size
    }
}
