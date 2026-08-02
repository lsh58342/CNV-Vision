package com.example.cnv.dwg

import org.kabeja.dxf.DXFArc
import org.kabeja.dxf.DXFBlock
import org.kabeja.dxf.DXFCircle
import org.kabeja.dxf.DXFConstants
import org.kabeja.dxf.DXFDocument
import org.kabeja.dxf.DXFEntity
import org.kabeja.dxf.DXFLayer
import org.kabeja.dxf.DXFLine
import org.kabeja.dxf.DXFLWPolyline
import org.kabeja.dxf.DXFPolyline
import org.kabeja.dxf.DXFText
import org.kabeja.dxf.DXFVertex
import org.kabeja.parser.DXFParser
import org.kabeja.parser.ParserBuilder
import java.io.File
import java.io.FileInputStream

/**
 * Open-source DXF adapter — Kabeja → [DWGEntityBag].
 * Reads ENTITIES (via layers) and BLOCKS geometry (no block edit / INSERT explode).
 */
class DxfReader : DWGReader {

    override fun open(sourcePath: String): DWGDocument {
        val file = resolveFile(sourcePath)
        val parser = ParserBuilder.createDefaultParser()
        FileInputStream(file).use { input ->
            parser.parse(input, DXFParser.DEFAULT_ENCODING)
        }
        val doc = parser.getDocument()
            ?: throw IllegalArgumentException("Kabeja returned no document for $sourcePath")
        return DWGDocument(
            sourcePath = file.absolutePath,
            fileName = file.name,
            nativeHandle = doc,
        )
    }

    override fun readLayers(document: DWGDocument): List<DWGLayer> {
        val doc = requireDoc(document)
        val names = LinkedHashSet<String>()
        val it = doc.getDXFLayerIterator()
        while (it.hasNext()) {
            val layer = it.next() as DXFLayer
            val name = layer.name?.takeIf { it.isNotBlank() } ?: DXFConstants.DEFAULT_LAYER
            names.add(name)
        }
        collectEntityBag(doc).let { bag ->
            bag.polylines.forEach { names.add(it.layerName) }
            bag.lines.forEach { names.add(it.layerName) }
            bag.arcs.forEach { names.add(it.layerName) }
            bag.circles.forEach { names.add(it.layerName) }
            bag.texts.forEach { names.add(it.layerName) }
        }
        if (names.isEmpty()) {
            names.add(DXFConstants.DEFAULT_LAYER)
        }
        return names.map { DWGLayer(it) }
    }

    override fun readEntities(document: DWGDocument): DWGEntityBag {
        return collectEntityBag(requireDoc(document))
    }

    private fun collectEntityBag(doc: DXFDocument): DWGEntityBag {
        val polylines = ArrayList<PolylineModel>()
        val lines = ArrayList<LineModel>()
        val arcs = ArrayList<ArcModel>()
        val circles = ArrayList<CircleModel>()
        val texts = ArrayList<TextModel>()
        var seq = 0
        fun nextId(prefix: String): String = "$prefix-${seq++}"

        val layerIt = doc.getDXFLayerIterator()
        while (layerIt.hasNext()) {
            val layer = layerIt.next() as DXFLayer
            val layerFallback = layer.name?.takeIf { it.isNotBlank() } ?: DXFConstants.DEFAULT_LAYER
            appendTypedEntities(
                layer = layer,
                type = DXFConstants.ENTITY_TYPE_LINE,
                layerFallback = layerFallback,
                idHint = null,
                polylines = polylines,
                lines = lines,
                arcs = arcs,
                circles = circles,
                texts = texts,
                nextId = ::nextId,
            )
            appendTypedEntities(
                layer = layer,
                type = DXFConstants.ENTITY_TYPE_LWPOLYLINE,
                layerFallback = layerFallback,
                idHint = null,
                polylines = polylines,
                lines = lines,
                arcs = arcs,
                circles = circles,
                texts = texts,
                nextId = ::nextId,
            )
            appendTypedEntities(
                layer = layer,
                type = DXFConstants.ENTITY_TYPE_POLYLINE,
                layerFallback = layerFallback,
                idHint = null,
                polylines = polylines,
                lines = lines,
                arcs = arcs,
                circles = circles,
                texts = texts,
                nextId = ::nextId,
                skipLwPolyline = true,
            )
            appendTypedEntities(
                layer = layer,
                type = DXFConstants.ENTITY_TYPE_ARC,
                layerFallback = layerFallback,
                idHint = null,
                polylines = polylines,
                lines = lines,
                arcs = arcs,
                circles = circles,
                texts = texts,
                nextId = ::nextId,
            )
            appendTypedEntities(
                layer = layer,
                type = DXFConstants.ENTITY_TYPE_CIRCLE,
                layerFallback = layerFallback,
                idHint = null,
                polylines = polylines,
                lines = lines,
                arcs = arcs,
                circles = circles,
                texts = texts,
                nextId = ::nextId,
            )
            appendTypedEntities(
                layer = layer,
                type = DXFConstants.ENTITY_TYPE_TEXT,
                layerFallback = layerFallback,
                idHint = null,
                polylines = polylines,
                lines = lines,
                arcs = arcs,
                circles = circles,
                texts = texts,
                nextId = ::nextId,
            )
        }

        val blockIt = doc.getDXFBlockIterator()
        while (blockIt.hasNext()) {
            val block = blockIt.next() as DXFBlock
            val blockName = block.name.orEmpty()
            if (shouldSkipBlock(blockName)) continue
            val entityIt = block.getDXFEntitiesIterator() ?: continue
            while (entityIt.hasNext()) {
                val entity = entityIt.next() as? DXFEntity ?: continue
                appendOneEntity(
                    entity = entity,
                    layerFallback = entity.layerName?.takeIf { it.isNotBlank() }
                        ?: DXFConstants.DEFAULT_LAYER,
                    idHint = blockName,
                    polylines = polylines,
                    lines = lines,
                    arcs = arcs,
                    circles = circles,
                    texts = texts,
                    nextId = ::nextId,
                )
            }
        }

        return DWGEntityBag(
            polylines = polylines,
            lines = lines,
            arcs = arcs,
            circles = circles,
            texts = texts,
            blocks = emptyList(),
        )
    }

    private fun appendTypedEntities(
        layer: DXFLayer,
        type: String,
        layerFallback: String,
        idHint: String?,
        polylines: MutableList<PolylineModel>,
        lines: MutableList<LineModel>,
        arcs: MutableList<ArcModel>,
        circles: MutableList<CircleModel>,
        texts: MutableList<TextModel>,
        nextId: (String) -> String,
        skipLwPolyline: Boolean = false,
    ) {
        @Suppress("UNCHECKED_CAST")
        val list = layer.getDXFEntities(type) as? List<*> ?: return
        list.forEach { raw ->
            if (skipLwPolyline && raw is DXFLWPolyline) return@forEach
            val entity = raw as? DXFEntity ?: return@forEach
            appendOneEntity(
                entity = entity,
                layerFallback = layerFallback,
                idHint = idHint,
                polylines = polylines,
                lines = lines,
                arcs = arcs,
                circles = circles,
                texts = texts,
                nextId = nextId,
            )
        }
    }

    private fun appendOneEntity(
        entity: DXFEntity,
        layerFallback: String,
        idHint: String?,
        polylines: MutableList<PolylineModel>,
        lines: MutableList<LineModel>,
        arcs: MutableList<ArcModel>,
        circles: MutableList<CircleModel>,
        texts: MutableList<TextModel>,
        nextId: (String) -> String,
    ) {
        val layerName = entity.layerName?.takeIf { it.isNotBlank() } ?: layerFallback
        val hint = idHint?.takeIf { it.isNotBlank() }?.replace(Regex("[^A-Za-z0-9_-]"), "_")
        when (entity) {
            is DXFLine -> {
                val start = entity.startPoint ?: return
                val end = entity.endPoint ?: return
                val prefix = if (hint != null) "blk-$hint-ln" else "ln"
                lines.add(
                    LineModel(
                        id = nextId(prefix),
                        layerName = layerName,
                        start = Point2d(start.x, start.y),
                        end = Point2d(end.x, end.y),
                    ),
                )
            }
            is DXFLWPolyline, is DXFPolyline -> {
                val pl = entity as DXFPolyline
                val prefix = if (hint != null) {
                    if (entity is DXFLWPolyline) "blk-$hint-lwpl" else "blk-$hint-pl"
                } else {
                    if (entity is DXFLWPolyline) "lwpl" else "pl"
                }
                toPolylineModel(pl, nextId(prefix), layerName)?.let { polylines.add(it) }
            }
            is DXFArc -> {
                val center = entity.centerPoint ?: return
                val prefix = if (hint != null) "blk-$hint-arc" else "arc"
                arcs.add(
                    ArcModel(
                        id = nextId(prefix),
                        layerName = layerName,
                        center = Point2d(center.x, center.y),
                        radius = entity.radius,
                        startAngleDeg = entity.startAngle,
                        endAngleDeg = entity.endAngle,
                    ),
                )
            }
            is DXFCircle -> {
                val center = entity.centerPoint ?: return
                val prefix = if (hint != null) "blk-$hint-cir" else "cir"
                circles.add(
                    CircleModel(
                        id = nextId(prefix),
                        layerName = layerName,
                        center = Point2d(center.x, center.y),
                        radius = entity.radius,
                    ),
                )
            }
            is DXFText -> {
                val insert = entity.insertPoint ?: return
                val prefix = if (hint != null) "blk-$hint-txt" else "txt"
                texts.add(
                    TextModel(
                        id = nextId(prefix),
                        layerName = layerName,
                        position = Point2d(insert.x, insert.y),
                        content = entity.text.orEmpty(),
                        height = entity.height.takeIf { it > 0.0 } ?: 1.0,
                    ),
                )
            }
        }
    }

    private fun shouldSkipBlock(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return true
        if (n.equals("*Model_Space", ignoreCase = true)) return true
        if (n.startsWith("*Paper_Space", ignoreCase = true)) return true
        return false
    }

    private fun toPolylineModel(
        pl: DXFPolyline,
        id: String,
        layerFallback: String,
    ): PolylineModel? {
        val points = ArrayList<Point2d>()
        val count = pl.vertexCount
        for (i in 0 until count) {
            val vertex: DXFVertex = pl.getVertex(i) ?: continue
            if (vertex.isFaceRecord) continue
            points.add(Point2d(vertex.x, vertex.y))
        }
        if (points.size < 2) return null
        return PolylineModel(
            id = id,
            layerName = pl.layerName?.takeIf { it.isNotBlank() } ?: layerFallback,
            points = points,
            closed = pl.isClosed,
        )
    }

    private fun requireDoc(document: DWGDocument): DXFDocument {
        return document.nativeHandle as? DXFDocument
            ?: throw IllegalArgumentException("Document was not opened by DxfReader")
    }

    private fun resolveFile(sourcePath: String): File {
        val file = File(sourcePath)
        if (!file.isFile) {
            throw IllegalArgumentException("DXF file not found: $sourcePath")
        }
        if (!file.name.endsWith(".dxf", ignoreCase = true)) {
            throw IllegalArgumentException("Not a DXF file: ${file.name}")
        }
        return file
    }
}
