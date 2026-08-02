package com.example.cnv.dwg

import org.kabeja.dxf.DXFArc
import org.kabeja.dxf.DXFCircle
import org.kabeja.dxf.DXFConstants
import org.kabeja.dxf.DXFDocument
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
 * Does not parse DXF itself; wraps [org.kabeja] only.
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
        val out = ArrayList<DWGLayer>()
        val it = doc.getDXFLayerIterator()
        while (it.hasNext()) {
            val layer = it.next() as DXFLayer
            val name = layer.name?.takeIf { it.isNotBlank() } ?: DXFConstants.DEFAULT_LAYER
            out.add(DWGLayer(name))
        }
        if (out.isEmpty()) {
            out.add(DWGLayer(DXFConstants.DEFAULT_LAYER))
        }
        return out
    }

    override fun readEntities(document: DWGDocument): DWGEntityBag {
        val doc = requireDoc(document)
        val polylines = ArrayList<PolylineModel>()
        val lines = ArrayList<LineModel>()
        val arcs = ArrayList<ArcModel>()
        val circles = ArrayList<CircleModel>()
        val texts = ArrayList<TextModel>()
        var seq = 0

        val layerIt = doc.getDXFLayerIterator()
        while (layerIt.hasNext()) {
            val layer = layerIt.next() as DXFLayer
            val layerFallback = layer.name?.takeIf { it.isNotBlank() } ?: DXFConstants.DEFAULT_LAYER

            @Suppress("UNCHECKED_CAST")
            val lineEntities = layer.getDXFEntities(DXFConstants.ENTITY_TYPE_LINE) as? List<*>
            lineEntities.orEmpty().forEach { entity ->
                val line = entity as? DXFLine ?: return@forEach
                val start = line.startPoint ?: return@forEach
                val end = line.endPoint ?: return@forEach
                lines.add(
                    LineModel(
                        id = "ln-${seq++}",
                        layerName = line.layerName?.takeIf { it.isNotBlank() } ?: layerFallback,
                        start = Point2d(start.x, start.y),
                        end = Point2d(end.x, end.y),
                    ),
                )
            }

            @Suppress("UNCHECKED_CAST")
            val lwEntities = layer.getDXFEntities(DXFConstants.ENTITY_TYPE_LWPOLYLINE) as? List<*>
            lwEntities.orEmpty().forEach { entity ->
                val pl = entity as? DXFLWPolyline ?: return@forEach
                toPolylineModel(pl, "lwpl-${seq++}", layerFallback)?.let { polylines.add(it) }
            }

            @Suppress("UNCHECKED_CAST")
            val plEntities = layer.getDXFEntities(DXFConstants.ENTITY_TYPE_POLYLINE) as? List<*>
            plEntities.orEmpty().forEach { entity ->
                if (entity is DXFLWPolyline) return@forEach
                val pl = entity as? DXFPolyline ?: return@forEach
                toPolylineModel(pl, "pl-${seq++}", layerFallback)?.let { polylines.add(it) }
            }

            @Suppress("UNCHECKED_CAST")
            val arcEntities = layer.getDXFEntities(DXFConstants.ENTITY_TYPE_ARC) as? List<*>
            arcEntities.orEmpty().forEach { entity ->
                val arc = entity as? DXFArc ?: return@forEach
                val center = arc.centerPoint ?: return@forEach
                arcs.add(
                    ArcModel(
                        id = "arc-${seq++}",
                        layerName = arc.layerName?.takeIf { it.isNotBlank() } ?: layerFallback,
                        center = Point2d(center.x, center.y),
                        radius = arc.radius,
                        startAngleDeg = arc.startAngle,
                        endAngleDeg = arc.endAngle,
                    ),
                )
            }

            @Suppress("UNCHECKED_CAST")
            val circleEntities = layer.getDXFEntities(DXFConstants.ENTITY_TYPE_CIRCLE) as? List<*>
            circleEntities.orEmpty().forEach { entity ->
                val circle = entity as? DXFCircle ?: return@forEach
                val center = circle.centerPoint ?: return@forEach
                circles.add(
                    CircleModel(
                        id = "cir-${seq++}",
                        layerName = circle.layerName?.takeIf { it.isNotBlank() } ?: layerFallback,
                        center = Point2d(center.x, center.y),
                        radius = circle.radius,
                    ),
                )
            }

            @Suppress("UNCHECKED_CAST")
            val textEntities = layer.getDXFEntities(DXFConstants.ENTITY_TYPE_TEXT) as? List<*>
            textEntities.orEmpty().forEach { entity ->
                val text = entity as? DXFText ?: return@forEach
                val insert = text.insertPoint ?: return@forEach
                texts.add(
                    TextModel(
                        id = "txt-${seq++}",
                        layerName = text.layerName?.takeIf { it.isNotBlank() } ?: layerFallback,
                        position = Point2d(insert.x, insert.y),
                        content = text.text.orEmpty(),
                        height = text.height.takeIf { it > 0.0 } ?: 1.0,
                    ),
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
