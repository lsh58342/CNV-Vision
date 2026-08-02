package com.example.cnv.dwg

import org.kabeja.dxf.DXFArc
import org.kabeja.dxf.DXFBlock
import org.kabeja.dxf.DXFCircle
import org.kabeja.dxf.DXFConstants
import org.kabeja.dxf.DXFDocument
import org.kabeja.dxf.DXFEntity
import org.kabeja.dxf.DXFInsert
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Open-source DXF adapter — Kabeja → [DWGEntityBag].
 *
 * - Model ENTITIES: LINE / LWPOLYLINE / POLYLINE / ARC / CIRCLE / TEXT
 * - BLOCK + INSERT: definitions are flattened through INSERT
 *   (position / rotation / scale); not kept as separate Block objects.
 */
class DxfReader : DWGReader {

    /** Diagnostics filled on last [readEntities] / [readLayers] collect. */
    data class CollectStats(
        val blockDefinitionCount: Int = 0,
        val insertCount: Int = 0,
        val flattenedFromInsert: Int = 0,
    )

    @Volatile
    var lastStats: CollectStats = CollectStats()
        private set

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
            names.add(layer.name?.takeIf { it.isNotBlank() } ?: DXFConstants.DEFAULT_LAYER)
        }
        collectEntityBag(doc).let { bag ->
            bag.polylines.forEach { names.add(it.layerName) }
            bag.lines.forEach { names.add(it.layerName) }
            bag.arcs.forEach { names.add(it.layerName) }
            bag.circles.forEach { names.add(it.layerName) }
            bag.texts.forEach { names.add(it.layerName) }
        }
        if (names.isEmpty()) names.add(DXFConstants.DEFAULT_LAYER)
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

        var blockDefs = 0
        val blockIt = doc.getDXFBlockIterator()
        while (blockIt.hasNext()) {
            val block = blockIt.next() as DXFBlock
            if (!shouldSkipBlock(block.name.orEmpty())) blockDefs++
        }

        var insertCount = 0
        var flattened = 0

        val layerIt = doc.getDXFLayerIterator()
        while (layerIt.hasNext()) {
            val layer = layerIt.next() as DXFLayer
            val layerFallback = layer.name?.takeIf { it.isNotBlank() } ?: DXFConstants.DEFAULT_LAYER

            for (type in MODEL_ENTITY_TYPES) {
                appendTypedModelEntities(
                    layer = layer,
                    type = type,
                    layerFallback = layerFallback,
                    polylines = polylines,
                    lines = lines,
                    arcs = arcs,
                    circles = circles,
                    texts = texts,
                    nextId = ::nextId,
                    skipLwPolyline = type == DXFConstants.ENTITY_TYPE_POLYLINE,
                )
            }

            @Suppress("UNCHECKED_CAST")
            val inserts = layer.getDXFEntities(DXFConstants.ENTITY_TYPE_INSERT) as? List<*>
            inserts.orEmpty().forEach { raw ->
                val insert = raw as? DXFInsert ?: return@forEach
                insertCount++
                flattened += explodeInsert(
                    doc = doc,
                    insert = insert,
                    insertLayerFallback = layerFallback,
                    polylines = polylines,
                    lines = lines,
                    arcs = arcs,
                    circles = circles,
                    texts = texts,
                    nextId = ::nextId,
                    depth = 0,
                )
            }
        }

        lastStats = CollectStats(
            blockDefinitionCount = blockDefs,
            insertCount = insertCount,
            flattenedFromInsert = flattened,
        )

        return DWGEntityBag(
            polylines = polylines,
            lines = lines,
            arcs = arcs,
            circles = circles,
            texts = texts,
            blocks = emptyList(),
        )
    }

    private fun explodeInsert(
        doc: DXFDocument,
        insert: DXFInsert,
        insertLayerFallback: String,
        polylines: MutableList<PolylineModel>,
        lines: MutableList<LineModel>,
        arcs: MutableList<ArcModel>,
        circles: MutableList<CircleModel>,
        texts: MutableList<TextModel>,
        nextId: (String) -> String,
        depth: Int,
    ): Int {
        if (depth > MAX_INSERT_DEPTH) return 0
        val blockId = insert.blockID?.takeIf { it.isNotBlank() } ?: return 0
        val block = doc.getDXFBlock(blockId) ?: return 0
        if (shouldSkipBlock(block.name.orEmpty())) return 0

        val base = block.referencePoint
        val baseX = base?.x ?: 0.0
        val baseY = base?.y ?: 0.0
        val sx = insert.scaleX.takeUnless { it == 0.0 } ?: 1.0
        val sy = insert.scaleY.takeUnless { it == 0.0 } ?: 1.0
        val rot = insert.rotate
        val insertLayer = insert.layerName?.takeIf { it.isNotBlank() } ?: insertLayerFallback
        val origin = insert.point
        val originX = origin?.x ?: 0.0
        val originY = origin?.y ?: 0.0

        val rows = max(1, insert.rows)
        val cols = max(1, insert.columns)
        val rowSpacing = insert.rowSpacing
        val colSpacing = insert.columnSpacing
        val blockHint = blockId.replace(Regex("[^A-Za-z0-9_-]"), "_")

        var added = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val localOffX = c * colSpacing
                val localOffY = r * rowSpacing
                val worldOff = rotateScale(localOffX, localOffY, sx = 1.0, sy = 1.0, rotDeg = rot)
                val xf = InsertXform(
                    originX = originX + worldOff.first,
                    originY = originY + worldOff.second,
                    scaleX = sx,
                    scaleY = sy,
                    rotDeg = rot,
                    baseX = baseX,
                    baseY = baseY,
                )
                val entityIt = block.getDXFEntitiesIterator() ?: continue
                while (entityIt.hasNext()) {
                    val entity = entityIt.next() as? DXFEntity ?: continue
                    when (entity) {
                        is DXFInsert -> {
                            // Nested INSERT: compose by exploding child with depth+1
                            // after baking parent xform into a synthetic approach —
                            // apply child relative to parent by transforming child insert point.
                            added += explodeNestedInsert(
                                doc = doc,
                                parentXf = xf,
                                parentLayer = insertLayer,
                                child = entity,
                                polylines = polylines,
                                lines = lines,
                                arcs = arcs,
                                circles = circles,
                                texts = texts,
                                nextId = nextId,
                                depth = depth + 1,
                            )
                        }
                        else -> {
                            added += appendTransformedEntity(
                                entity = entity,
                                xf = xf,
                                insertLayer = insertLayer,
                                idHint = "ins-$blockHint",
                                polylines = polylines,
                                lines = lines,
                                arcs = arcs,
                                circles = circles,
                                texts = texts,
                                nextId = nextId,
                            )
                        }
                    }
                }
            }
        }
        return added
    }

    private fun explodeNestedInsert(
        doc: DXFDocument,
        parentXf: InsertXform,
        parentLayer: String,
        child: DXFInsert,
        polylines: MutableList<PolylineModel>,
        lines: MutableList<LineModel>,
        arcs: MutableList<ArcModel>,
        circles: MutableList<CircleModel>,
        texts: MutableList<TextModel>,
        nextId: (String) -> String,
        depth: Int,
    ): Int {
        if (depth > MAX_INSERT_DEPTH) return 0
        val blockId = child.blockID?.takeIf { it.isNotBlank() } ?: return 0
        val block = doc.getDXFBlock(blockId) ?: return 0
        if (shouldSkipBlock(block.name.orEmpty())) return 0

        val childOrigin = child.point
        val local = transformPoint(
            childOrigin?.x ?: 0.0,
            childOrigin?.y ?: 0.0,
            parentXf,
        )
        val composed = InsertXform(
            originX = local.x,
            originY = local.y,
            scaleX = parentXf.scaleX * (child.scaleX.takeUnless { it == 0.0 } ?: 1.0),
            scaleY = parentXf.scaleY * (child.scaleY.takeUnless { it == 0.0 } ?: 1.0),
            rotDeg = parentXf.rotDeg + child.rotate,
            baseX = block.referencePoint?.x ?: 0.0,
            baseY = block.referencePoint?.y ?: 0.0,
        )
        val insertLayer = child.layerName?.takeIf { it.isNotBlank() } ?: parentLayer
        val blockHint = blockId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        var added = 0
        val entityIt = block.getDXFEntitiesIterator() ?: return 0
        while (entityIt.hasNext()) {
            val entity = entityIt.next() as? DXFEntity ?: continue
            if (entity is DXFInsert) {
                added += explodeNestedInsert(
                    doc = doc,
                    parentXf = composed,
                    parentLayer = insertLayer,
                    child = entity,
                    polylines = polylines,
                    lines = lines,
                    arcs = arcs,
                    circles = circles,
                    texts = texts,
                    nextId = nextId,
                    depth = depth + 1,
                )
            } else {
                added += appendTransformedEntity(
                    entity = entity,
                    xf = composed,
                    insertLayer = insertLayer,
                    idHint = "ins-$blockHint",
                    polylines = polylines,
                    lines = lines,
                    arcs = arcs,
                    circles = circles,
                    texts = texts,
                    nextId = nextId,
                )
            }
        }
        return added
    }

    private fun appendTransformedEntity(
        entity: DXFEntity,
        xf: InsertXform,
        insertLayer: String,
        idHint: String,
        polylines: MutableList<PolylineModel>,
        lines: MutableList<LineModel>,
        arcs: MutableList<ArcModel>,
        circles: MutableList<CircleModel>,
        texts: MutableList<TextModel>,
        nextId: (String) -> String,
    ): Int {
        val layerName = resolveEntityLayer(entity.layerName, insertLayer)
        when (entity) {
            is DXFLine -> {
                val start = entity.startPoint ?: return 0
                val end = entity.endPoint ?: return 0
                lines.add(
                    LineModel(
                        id = nextId("$idHint-ln"),
                        layerName = layerName,
                        start = transformPoint(start.x, start.y, xf),
                        end = transformPoint(end.x, end.y, xf),
                    ),
                )
                return 1
            }
            is DXFLWPolyline, is DXFPolyline -> {
                val pl = entity as DXFPolyline
                val points = ArrayList<Point2d>()
                for (i in 0 until pl.vertexCount) {
                    val v: DXFVertex = pl.getVertex(i) ?: continue
                    if (v.isFaceRecord) continue
                    points.add(transformPoint(v.x, v.y, xf))
                }
                if (points.size < 2) return 0
                val prefix = if (entity is DXFLWPolyline) "$idHint-lwpl" else "$idHint-pl"
                polylines.add(
                    PolylineModel(
                        id = nextId(prefix),
                        layerName = layerName,
                        points = points,
                        closed = pl.isClosed,
                    ),
                )
                return 1
            }
            is DXFArc -> {
                val center = entity.centerPoint ?: return 0
                val c = transformPoint(center.x, center.y, xf)
                val scale = uniformScale(xf)
                arcs.add(
                    ArcModel(
                        id = nextId("$idHint-arc"),
                        layerName = layerName,
                        center = c,
                        radius = abs(entity.radius * scale),
                        startAngleDeg = normalizeAngle(entity.startAngle + xf.rotDeg),
                        endAngleDeg = normalizeAngle(entity.endAngle + xf.rotDeg),
                    ),
                )
                return 1
            }
            is DXFCircle -> {
                val center = entity.centerPoint ?: return 0
                val c = transformPoint(center.x, center.y, xf)
                circles.add(
                    CircleModel(
                        id = nextId("$idHint-cir"),
                        layerName = layerName,
                        center = c,
                        radius = abs(entity.radius * uniformScale(xf)),
                    ),
                )
                return 1
            }
            is DXFText -> {
                val insert = entity.insertPoint ?: return 0
                texts.add(
                    TextModel(
                        id = nextId("$idHint-txt"),
                        layerName = layerName,
                        position = transformPoint(insert.x, insert.y, xf),
                        content = entity.text.orEmpty(),
                        height = abs((entity.height.takeIf { it > 0.0 } ?: 1.0) * abs(xf.scaleY)),
                    ),
                )
                return 1
            }
            else -> return 0
        }
    }

    private fun appendTypedModelEntities(
        layer: DXFLayer,
        type: String,
        layerFallback: String,
        polylines: MutableList<PolylineModel>,
        lines: MutableList<LineModel>,
        arcs: MutableList<ArcModel>,
        circles: MutableList<CircleModel>,
        texts: MutableList<TextModel>,
        nextId: (String) -> String,
        skipLwPolyline: Boolean,
    ) {
        @Suppress("UNCHECKED_CAST")
        val list = layer.getDXFEntities(type) as? List<*> ?: return
        list.forEach { raw ->
            if (skipLwPolyline && raw is DXFLWPolyline) return@forEach
            val entity = raw as? DXFEntity ?: return@forEach
            appendModelEntity(
                entity = entity,
                layerFallback = layerFallback,
                polylines = polylines,
                lines = lines,
                arcs = arcs,
                circles = circles,
                texts = texts,
                nextId = nextId,
            )
        }
    }

    private fun appendModelEntity(
        entity: DXFEntity,
        layerFallback: String,
        polylines: MutableList<PolylineModel>,
        lines: MutableList<LineModel>,
        arcs: MutableList<ArcModel>,
        circles: MutableList<CircleModel>,
        texts: MutableList<TextModel>,
        nextId: (String) -> String,
    ) {
        val layerName = entity.layerName?.takeIf { it.isNotBlank() } ?: layerFallback
        when (entity) {
            is DXFLine -> {
                val start = entity.startPoint ?: return
                val end = entity.endPoint ?: return
                lines.add(
                    LineModel(
                        id = nextId("ln"),
                        layerName = layerName,
                        start = Point2d(start.x, start.y),
                        end = Point2d(end.x, end.y),
                    ),
                )
            }
            is DXFLWPolyline, is DXFPolyline -> {
                val pl = entity as DXFPolyline
                val points = ArrayList<Point2d>()
                for (i in 0 until pl.vertexCount) {
                    val v: DXFVertex = pl.getVertex(i) ?: continue
                    if (v.isFaceRecord) continue
                    points.add(Point2d(v.x, v.y))
                }
                if (points.size < 2) return
                polylines.add(
                    PolylineModel(
                        id = nextId(if (entity is DXFLWPolyline) "lwpl" else "pl"),
                        layerName = layerName,
                        points = points,
                        closed = pl.isClosed,
                    ),
                )
            }
            is DXFArc -> {
                val center = entity.centerPoint ?: return
                arcs.add(
                    ArcModel(
                        id = nextId("arc"),
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
                circles.add(
                    CircleModel(
                        id = nextId("cir"),
                        layerName = layerName,
                        center = Point2d(center.x, center.y),
                        radius = entity.radius,
                    ),
                )
            }
            is DXFText -> {
                val insert = entity.insertPoint ?: return
                texts.add(
                    TextModel(
                        id = nextId("txt"),
                        layerName = layerName,
                        position = Point2d(insert.x, insert.y),
                        content = entity.text.orEmpty(),
                        height = entity.height.takeIf { it > 0.0 } ?: 1.0,
                    ),
                )
            }
        }
    }

    private fun resolveEntityLayer(entityLayer: String?, insertLayer: String): String {
        val name = entityLayer?.takeIf { it.isNotBlank() } ?: return insertLayer
        // AutoCAD convention: layer "0" inside a block inherits INSERT layer.
        return if (name == "0") insertLayer else name
    }

    private fun transformPoint(x: Double, y: Double, xf: InsertXform): Point2d {
        val scaled = rotateScale(
            x = x - xf.baseX,
            y = y - xf.baseY,
            sx = xf.scaleX,
            sy = xf.scaleY,
            rotDeg = xf.rotDeg,
        )
        return Point2d(scaled.first + xf.originX, scaled.second + xf.originY)
    }

    private fun rotateScale(
        x: Double,
        y: Double,
        sx: Double,
        sy: Double,
        rotDeg: Double,
    ): Pair<Double, Double> {
        val px = x * sx
        val py = y * sy
        val rad = rotDeg * PI / 180.0
        val c = cos(rad)
        val s = sin(rad)
        return Pair(px * c - py * s, px * s + py * c)
    }

    private fun uniformScale(xf: InsertXform): Double =
        (abs(xf.scaleX) + abs(xf.scaleY)) * 0.5

    private fun normalizeAngle(deg: Double): Double {
        var a = deg % 360.0
        if (a < 0) a += 360.0
        return a
    }

    private fun shouldSkipBlock(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return true
        if (n.equals("*Model_Space", ignoreCase = true)) return true
        if (n.startsWith("*Paper_Space", ignoreCase = true)) return true
        return false
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

    private data class InsertXform(
        val originX: Double,
        val originY: Double,
        val scaleX: Double,
        val scaleY: Double,
        val rotDeg: Double,
        val baseX: Double,
        val baseY: Double,
    )

    companion object {
        private const val MAX_INSERT_DEPTH = 8
        private val MODEL_ENTITY_TYPES = listOf(
            DXFConstants.ENTITY_TYPE_LINE,
            DXFConstants.ENTITY_TYPE_LWPOLYLINE,
            DXFConstants.ENTITY_TYPE_POLYLINE,
            DXFConstants.ENTITY_TYPE_ARC,
            DXFConstants.ENTITY_TYPE_CIRCLE,
            DXFConstants.ENTITY_TYPE_TEXT,
        )
    }
}
