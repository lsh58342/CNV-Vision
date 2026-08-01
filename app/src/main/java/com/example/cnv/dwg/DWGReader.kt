package com.example.cnv.dwg

/**
 * Vendor-agnostic DWG document handle returned by [DWGReader].
 * Concrete readers (ODA, LibreDWG, …) supply their own payload via [nativeHandle].
 */
data class DWGDocument(
    val sourcePath: String,
    val fileName: String,
    val nativeHandle: Any? = null,
)

/**
 * Pluggable DWG binary reader. Implementations may be swapped without changing [DWGImporter].
 */
interface DWGReader {
    /**
     * Opens a DWG (or compatible) source and returns a document handle.
     * @throws IllegalArgumentException when the source cannot be opened.
     */
    fun open(sourcePath: String): DWGDocument

    /**
     * Reads layers from an opened document.
     */
    fun readLayers(document: DWGDocument): List<DWGLayer>

    /**
     * Reads raw entity bags used by [GeometryExtractor].
     */
    fun readEntities(document: DWGDocument): DWGEntityBag
}

/**
 * Intermediate entity bag produced by a [DWGReader] before geometry normalization.
 */
data class DWGEntityBag(
    val polylines: List<PolylineModel> = emptyList(),
    val lines: List<LineModel> = emptyList(),
    val arcs: List<ArcModel> = emptyList(),
    val circles: List<CircleModel> = emptyList(),
    val texts: List<TextModel> = emptyList(),
    val blocks: List<BlockModel> = emptyList(),
)
