package com.example.cnv.route

import com.example.cnv.core.model.RouteDirection
import com.example.cnv.dwg.DWGConfig
import com.example.cnv.dwg.DWGEntityBag
import com.example.cnv.dwg.DWGImporter
import com.example.cnv.dwg.DWGLayer
import com.example.cnv.dwg.DxfReader
import com.example.cnv.dwg.GeometryExtractor
import com.example.cnv.dwg.Point2d
import com.example.cnv.dwg.PolylineModel
import com.example.cnv.map.RoutePosition
import com.example.cnv.map.RouteRepository
import com.example.cnv.route.RouteCandidate
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * STEP 20-19 — Trace where ㄷ (U-shape) collapses to a straight line.
 */
class RouteGeometryTraceTest {

    @Test
    fun traceSyntheticUShape_whereDoesItCollapse() {
        println("======== SYNTHETIC ㄷ (U) TRACE ========")
        val uVertices = listOf(
            Point2d(0.0, 0.0),
            Point2d(1000.0, 0.0),
            Point2d(1000.0, 500.0),
            Point2d(0.0, 500.0),
        )
        uVertices.forEachIndexed { i, p -> println("INPUT V$i (${fmt(p.x)}, ${fmt(p.y)})") }

        val raw = PolylineModel(id = "u0", layerName = "CONVEYOR", points = uVertices)
        println("LOG[1_DXF_READER] Polyline count=1 verts=${raw.points.size}")
        raw.points.forEachIndexed { i, p ->
            println("LOG[1_DXF_READER] Polyline 0 V$i (${fmt(p.x)}, ${fmt(p.y)})")
        }

        val geometry = GeometryExtractor().extract(
            "synthetic_u.dxf",
            listOf(DWGLayer(name = "CONVEYOR")),
            DWGEntityBag(polylines = listOf(raw)),
        )
        println("LOG[2_GEOMETRY] polylineCount=${geometry.polylines.size}")
        geometry.polylines.forEachIndexed { pi, pl ->
            println("LOG[2_GEOMETRY] P$pi verts=${pl.points.size} dropped=${raw.points.size - pl.points.size}")
            pl.points.forEachIndexed { i, p ->
                println("LOG[2_GEOMETRY] P$pi V$i (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        val extraction = RouteExtractor().extract(geometry, "CONVEYOR")
        println("LOG[3_MERGE_BEFORE] count=${extraction.sourcePolylines.size}")
        extraction.sourcePolylines.forEachIndexed { i, pl ->
            pl.points.forEachIndexed { vi, p ->
                println("LOG[3_MERGE_BEFORE] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }
        println("LOG[3_MERGE_AFTER] count=${extraction.mergedPolylines.size}")
        extraction.mergedPolylines.forEachIndexed { i, pl ->
            println(
                "LOG[3_MERGE_AFTER] P$i verts=${pl.points.size} " +
                    "straight=${isStraight(pl.points)} bends=${bendCount(pl.points)}",
            )
            pl.points.forEachIndexed { vi, p ->
                println("LOG[3_MERGE_AFTER] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        println("LOG[4_CENTERLINE] count=${extraction.centerLines.size}")
        extraction.centerLines.forEachIndexed { i, cl ->
            println(
                "LOG[4_CENTERLINE] cl$i source=${cl.sourceCount} verts=${cl.points.size} " +
                    "straight=${isStraight(cl.points)} bends=${bendCount(cl.points)}",
            )
            cl.points.forEachIndexed { vi, p ->
                println("LOG[4_CENTERLINE] cl$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        println("LOG[5_CANDIDATE] count=${extraction.candidates.size}")
        extraction.candidates.forEachIndexed { i, c ->
            println(
                "LOG[5_CANDIDATE] #$i verts=${c.centerLine.size} " +
                    "straight=${isStraight(c.centerLine)} bends=${bendCount(c.centerLine)}",
            )
            c.centerLine.forEachIndexed { vi, p ->
                println("LOG[5_CANDIDATE] #$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        val draft = RouteBuilder().build(extraction.candidates, "u-route")
        println("LOG[6_ROUTEBUILDER] segments=${draft.segments.size}")
        draft.segments.forEachIndexed { i, s ->
            println(
                "LOG[6_ROUTEBUILDER] S${i + 1}\n" +
                    "  (${fmt(s.start.x)}, ${fmt(s.start.y)})\n" +
                    "  ↓\n" +
                    "  (${fmt(s.end.x)}, ${fmt(s.end.y)})",
            )
        }

        val generated = RouteGenerator(RouteRepository()).generate(extraction.candidates, "u-route")!!
        dumpChain(generated, "7_MAPPER")
        dumpChain(generated, "8_FINAL_ROUTE")
        dumpBBox(generated, "9_RENDER")

        val stage = verdict(
            srcBends = bendCount(geometry.polylines.first().points),
            mergedBends = bendCount(extraction.mergedPolylines.first().points),
            centerBends = extraction.centerLines.maxOfOrNull { bendCount(it.points) } ?: 0,
            candBends = extraction.candidates.maxOfOrNull { bendCount(it.centerLine) } ?: 0,
            builderSegs = draft.segments.size,
            finalSegs = generated.segmentCount,
        )
        println("LOG[VERDICT_SYNTHETIC] $stage")
        assertTrue(generated.segmentCount >= 3)
    }

    @Test
    fun traceThreeSeparateLines_uShape_findCollapse() {
        println("======== ㄷ AS 3 LINE SEGMENTS ========")
        val lines = listOf(
            PolylineModel("l1", "CONVEYOR", listOf(Point2d(0.0, 0.0), Point2d(1000.0, 0.0))),
            PolylineModel("l2", "CONVEYOR", listOf(Point2d(1000.0, 0.0), Point2d(1000.0, 500.0))),
            PolylineModel("l3", "CONVEYOR", listOf(Point2d(1000.0, 500.0), Point2d(0.0, 500.0))),
        )
        lines.forEachIndexed { i, pl ->
            println("LOG[1_DXF_READER] Polyline $i")
            pl.points.forEachIndexed { vi, p ->
                println("LOG[1_DXF_READER] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }
        val geometry = GeometryExtractor().extract(
            "u_lines.dxf",
            listOf(DWGLayer(name = "CONVEYOR")),
            DWGEntityBag(polylines = lines),
        )
        println("LOG[2_GEOMETRY] count=${geometry.polylines.size}")
        geometry.polylines.forEachIndexed { i, pl ->
            pl.points.forEachIndexed { vi, p ->
                println("LOG[2_GEOMETRY] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        val extraction = RouteExtractor().extract(geometry, "CONVEYOR")
        println("LOG[3_MERGE_AFTER] count=${extraction.mergedPolylines.size}")
        extraction.mergedPolylines.forEachIndexed { i, pl ->
            println(
                "LOG[3_MERGE_AFTER] P$i verts=${pl.points.size} " +
                    "straight=${isStraight(pl.points)} bends=${bendCount(pl.points)}",
            )
            pl.points.forEachIndexed { vi, p ->
                println("LOG[3_MERGE_AFTER] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }
        println("LOG[4_CENTERLINE] count=${extraction.centerLines.size}")
        extraction.centerLines.forEachIndexed { i, cl ->
            println(
                "LOG[4_CENTERLINE] cl$i source=${cl.sourceCount} verts=${cl.points.size} " +
                    "straight=${isStraight(cl.points)} bends=${bendCount(cl.points)}",
            )
            cl.points.forEachIndexed { vi, p ->
                println("LOG[4_CENTERLINE] cl$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }
        extraction.candidates.forEachIndexed { i, c ->
            println(
                "LOG[5_CANDIDATE] #$i verts=${c.centerLine.size} " +
                    "straight=${isStraight(c.centerLine)} bends=${bendCount(c.centerLine)}",
            )
            c.centerLine.forEachIndexed { vi, p ->
                println("LOG[5_CANDIDATE] #$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }
        val draft = RouteBuilder().build(extraction.candidates, "u-lines")
        draft.segments.forEachIndexed { i, s ->
            println(
                "LOG[6_ROUTEBUILDER] S${i + 1}\n" +
                    "  (${fmt(s.start.x)}, ${fmt(s.start.y)})\n" +
                    "  ↓\n" +
                    "  (${fmt(s.end.x)}, ${fmt(s.end.y)})",
            )
        }
        val gen = RouteGenerator(RouteRepository()).generate(extraction.candidates, "u-lines")!!
        dumpChain(gen, "8_FINAL")
        val stage = verdict(
            srcBends = extraction.sourcePolylines.maxOfOrNull { bendCount(it.points) } ?: 0,
            mergedBends = extraction.mergedPolylines.maxOfOrNull { bendCount(it.points) } ?: 0,
            centerBends = extraction.centerLines.maxOfOrNull { bendCount(it.points) } ?: 0,
            candBends = extraction.candidates.maxOfOrNull { bendCount(it.centerLine) } ?: 0,
            builderSegs = draft.segments.size,
            finalSegs = gen.segmentCount,
        )
        println("LOG[VERDICT_3LINES] $stage")
        assertTrue(gen.segmentCount >= 3)
    }

    @Test
    fun traceParallelDoubleRail_uShape_findCollapse() {
        println("======== ㄷ DOUBLE RAIL ========")
        val outer = PolylineModel(
            "outer",
            "CONVEYOR",
            listOf(
                Point2d(0.0, 0.0),
                Point2d(1000.0, 0.0),
                Point2d(1000.0, 500.0),
                Point2d(0.0, 500.0),
            ),
        )
        val inner = PolylineModel(
            "inner",
            "CONVEYOR",
            listOf(
                Point2d(0.0, 40.0),
                Point2d(960.0, 40.0),
                Point2d(960.0, 460.0),
                Point2d(0.0, 460.0),
            ),
        )
        println("LOG[1_DXF_READER] Polyline 0 (outer)")
        outer.points.forEachIndexed { i, p -> println("V$i (${fmt(p.x)}, ${fmt(p.y)})") }
        println("LOG[1_DXF_READER] Polyline 1 (inner)")
        inner.points.forEachIndexed { i, p -> println("V$i (${fmt(p.x)}, ${fmt(p.y)})") }
        val startGap = outer.points.first().distanceTo(inner.points.first())
        val endGap = outer.points.last().distanceTo(inner.points.last())
        println(
            "LOG[PAIR_CHECK] startGap=${fmt(startGap)} endGap=${fmt(endGap)} " +
                "limit=${DWGConfig.DEFAULT_CENTER_LINE_TOLERANCE * 2}",
        )

        val geometry = GeometryExtractor().extract(
            "double_u.dxf",
            listOf(DWGLayer(name = "CONVEYOR")),
            DWGEntityBag(polylines = listOf(outer, inner)),
        )
        geometry.polylines.forEachIndexed { i, pl ->
            println("LOG[2_GEOMETRY] P$i verts=${pl.points.size}")
            pl.points.forEachIndexed { vi, p ->
                println("LOG[2_GEOMETRY] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        val extraction = RouteExtractor().extract(geometry, "CONVEYOR")
        println("LOG[3_MERGE] merged=${extraction.mergedPolylines.size}")
        extraction.mergedPolylines.forEachIndexed { i, pl ->
            println("LOG[3_MERGE] P$i verts=${pl.points.size} bends=${bendCount(pl.points)}")
            pl.points.forEachIndexed { vi, p ->
                println("LOG[3_MERGE] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }
        println("LOG[4_CENTERLINE] count=${extraction.centerLines.size}")
        extraction.centerLines.forEachIndexed { i, cl ->
            println(
                "LOG[4_CENTERLINE] cl$i source=${cl.sourceCount} verts=${cl.points.size} " +
                    "straight=${isStraight(cl.points)} bends=${bendCount(cl.points)}",
            )
            cl.points.forEachIndexed { vi, p ->
                println("LOG[4_CENTERLINE] cl$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }
        val draft = RouteBuilder().build(extraction.candidates, "double")
        println("LOG[6_ROUTEBUILDER] segments=${draft.segments.size}")
        draft.segments.forEachIndexed { i, s ->
            println(
                "LOG[6_ROUTEBUILDER] S${i + 1}\n" +
                    "  (${fmt(s.start.x)}, ${fmt(s.start.y)})\n" +
                    "  ↓\n" +
                    "  (${fmt(s.end.x)}, ${fmt(s.end.y)})",
            )
        }
        val gen = RouteGenerator(RouteRepository()).generate(extraction.candidates)!!
        dumpChain(gen, "8_FINAL")
        val stage = verdict(
            srcBends = extraction.sourcePolylines.maxOfOrNull { bendCount(it.points) } ?: 0,
            mergedBends = extraction.mergedPolylines.maxOfOrNull { bendCount(it.points) } ?: 0,
            centerBends = extraction.centerLines.maxOfOrNull { bendCount(it.points) } ?: 0,
            candBends = extraction.candidates.maxOfOrNull { bendCount(it.centerLine) } ?: 0,
            builderSegs = draft.segments.size,
            finalSegs = gen.segmentCount,
        )
        println("LOG[VERDICT_DOUBLE_RAIL] $stage")
    }

    @Test
    fun traceRealTestDxf_dumpAllStages() {
        val dxf = resolveTestDxf()
        println("======== REAL test.dxf TRACE path=${dxf.absolutePath} ========")
        val reader = DxfReader()
        val doc = reader.open(dxf.absolutePath)
        val layers = reader.readLayers(doc)
        val bag = reader.readEntities(doc)

        println("LOG[1_DXF_READER] polylineCount=${bag.polylines.size} lineCount=${bag.lines.size}")
        bag.polylines.take(40).forEachIndexed { i, pl ->
            println(
                "LOG[1_DXF_READER] Polyline $i layer=${pl.layerName} verts=${pl.points.size} " +
                    "len=${fmt(pl.length())}",
            )
            pl.points.forEachIndexed { vi, p ->
                println("LOG[1_DXF_READER] Polyline $i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        val geometry = GeometryExtractor().extract(doc.fileName, layers, bag)
        println("LOG[2_GEOMETRY] polylines=${geometry.polylines.size}")
        geometry.polylines.take(40).forEachIndexed { i, pl ->
            val raw = bag.polylines.getOrNull(i)
            println(
                "LOG[2_GEOMETRY] P$i verts=${pl.points.size} raw=${raw?.points?.size} " +
                    "dropped=${(raw?.points?.size ?: pl.points.size) - pl.points.size}",
            )
            pl.points.forEachIndexed { vi, p ->
                println("LOG[2_GEOMETRY] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        val layerPick = layers.map { it.name }.distinct().maxByOrNull { name ->
            geometry.polylinesOnLayer(name).sumOf { it.points.size } +
                geometry.linesOnLayer(name).size
        } ?: DWGConfig.DEFAULT_LAYER_FILTER
        println("LOG[LAYER_PICK] $layerPick")

        val extraction = RouteExtractor().extract(geometry, layerPick)
        println("LOG[3_MERGE_BEFORE] source=${extraction.sourcePolylines.size}")
        extraction.sourcePolylines.forEachIndexed { i, pl ->
            println("LOG[3_MERGE_BEFORE] P$i verts=${pl.points.size} bends=${bendCount(pl.points)}")
            pl.points.forEachIndexed { vi, p ->
                println("LOG[3_MERGE_BEFORE] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }
        println("LOG[3_MERGE_AFTER] merged=${extraction.mergedPolylines.size}")
        extraction.mergedPolylines.forEachIndexed { i, pl ->
            println(
                "LOG[3_MERGE_AFTER] P$i verts=${pl.points.size} " +
                    "straight=${isStraight(pl.points)} bends=${bendCount(pl.points)}",
            )
            pl.points.forEachIndexed { vi, p ->
                println("LOG[3_MERGE_AFTER] P$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        println("LOG[4_CENTERLINE] count=${extraction.centerLines.size}")
        extraction.centerLines.forEachIndexed { i, cl ->
            println(
                "LOG[4_CENTERLINE] cl$i source=${cl.sourceCount} verts=${cl.points.size} " +
                    "straight=${isStraight(cl.points)} bends=${bendCount(cl.points)}",
            )
            cl.points.forEachIndexed { vi, p ->
                println("LOG[4_CENTERLINE] cl$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        println("LOG[5_CANDIDATE] count=${extraction.candidates.size}")
        extraction.candidates.forEachIndexed { i, c ->
            println(
                "LOG[5_CANDIDATE] #$i verts=${c.centerLine.size} " +
                    "straight=${isStraight(c.centerLine)} bends=${bendCount(c.centerLine)}",
            )
            c.centerLine.forEachIndexed { vi, p ->
                println("LOG[5_CANDIDATE] #$i V$vi (${fmt(p.x)}, ${fmt(p.y)})")
            }
        }

        if (extraction.candidates.isEmpty()) {
            println("LOG[VERDICT_REAL] no candidates")
            return
        }

        val draft = RouteBuilder().build(extraction.candidates, "real")
        println("LOG[6_ROUTEBUILDER] segments=${draft.segments.size}")
        draft.segments.forEachIndexed { i, s ->
            println(
                "LOG[6_ROUTEBUILDER] S${i + 1}\n" +
                    "  (${fmt(s.start.x)}, ${fmt(s.start.y)})\n" +
                    "  ↓\n" +
                    "  (${fmt(s.end.x)}, ${fmt(s.end.y)})",
            )
        }

        val gen = RouteGenerator(RouteRepository()).generate(extraction.candidates, "real")
        if (gen == null) {
            println("LOG[VERDICT_REAL] generator null")
            return
        }
        dumpChain(gen, "7_MAPPER")
        dumpChain(gen, "8_FINAL_ROUTE")
        dumpBBox(gen, "9_RENDER")

        val stage = verdict(
            srcBends = extraction.sourcePolylines.maxOfOrNull { bendCount(it.points) } ?: 0,
            mergedBends = extraction.mergedPolylines.maxOfOrNull { bendCount(it.points) } ?: 0,
            centerBends = extraction.centerLines.maxOfOrNull { bendCount(it.points) } ?: 0,
            candBends = extraction.candidates.maxOfOrNull { bendCount(it.centerLine) } ?: 0,
            builderSegs = draft.segments.size,
            finalSegs = gen.segmentCount,
        )
        println("LOG[COMPARE] $stage")
        println("LOG[VERDICT_REAL] $stage")

        val imported = DWGImporter(reader = DxfReader()).importFrom(dxf.absolutePath, layerPick)
        println(
            "LOG[IMPORT] candidates=${imported.candidates.size} " +
                "poly=${imported.polylineCount} merged=${imported.mergedPolylineCount}",
        )
    }

    private fun verdict(
        srcBends: Int,
        mergedBends: Int,
        centerBends: Int,
        candBends: Int,
        builderSegs: Int,
        finalSegs: Int,
    ): String {
        println(
            "LOG[COMPARE] srcBends=$srcBends mergedBends=$mergedBends " +
                "centerBends=$centerBends candBends=$candBends " +
                "builderSegs=$builderSegs finalSegs=$finalSegs",
        )
        return when {
            srcBends >= 2 && mergedBends < 2 -> "Merge"
            mergedBends >= 2 && centerBends < 2 -> "CenterLine"
            centerBends >= 2 && candBends < 2 -> "RouteCandidate"
            candBends >= 2 && builderSegs <= 1 -> "RouteBuilder"
            builderSegs > 1 && finalSegs <= 1 -> "CoordinateMapper"
            finalSegs >= 3 -> "NONE (preserved segs=$finalSegs)"
            else -> "UNKNOWN segs=$finalSegs"
        }
    }

    private fun dumpChain(gen: RouteImportResult, tag: String) {
        println("LOG[$tag] segmentCount=${gen.segmentCount}")
        for (seg in gen.route.segments) {
            val s = endpoint(gen.mapper, seg.id, 0f)
            val e = endpoint(gen.mapper, seg.id, 1f)
            println(
                "LOG[$tag] ${seg.id}\n" +
                    "  (${fmt(s?.x ?: 0.0)}, ${fmt(s?.y ?: 0.0)})\n" +
                    "  ↓\n" +
                    "  (${fmt(e?.x ?: 0.0)}, ${fmt(e?.y ?: 0.0)})",
            )
        }
    }

    private fun dumpBBox(gen: RouteImportResult, tag: String) {
        val pts = gen.route.segments.flatMap { seg ->
            listOfNotNull(endpoint(gen.mapper, seg.id, 0f), endpoint(gen.mapper, seg.id, 1f))
        }
        if (pts.isEmpty()) {
            println("LOG[$tag] empty")
            return
        }
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        println(
            "LOG[$tag] segmentCount=${gen.segmentCount} samples=${pts.size} " +
                "bbox=($minX,$minY)-($maxX,$maxY) w=${maxX - minX} h=${maxY - minY} " +
                "collapsedToLine=${abs(maxX - minX) < 1e-6 || abs(maxY - minY) < 1e-6}",
        )
    }

    private fun endpoint(mapper: CoordinateMapper, segmentId: String, progress: Float): WorldCoordinate? =
        mapper.toWorld(
            RoutePosition(
                segmentId = segmentId,
                nodeId = "",
                distanceFromSegmentStart = 0f,
                progress = progress,
                direction = RouteDirection.FORWARD,
                timestampNs = 0L,
                confidence = 1f,
            ),
        )

    private fun isStraight(points: List<Point2d>, tolDeg: Double = 8.0): Boolean =
        points.size < 3 || bendCount(points, tolDeg) == 0

    private fun bendCount(points: List<Point2d>, tolDeg: Double = 8.0): Int {
        if (points.size < 3) return 0
        var bends = 0
        val sinTol = kotlin.math.sin(Math.toRadians(tolDeg))
        for (i in 1 until points.lastIndex) {
            val a = points[i - 1]
            val b = points[i]
            val c = points[i + 1]
            val dx1 = b.x - a.x
            val dy1 = b.y - a.y
            val dx2 = c.x - b.x
            val dy2 = c.y - b.y
            val n1 = hypot(dx1, dy1)
            val n2 = hypot(dx2, dy2)
            if (n1 < 1e-9 || n2 < 1e-9) continue
            val cross = abs(dx1 * dy2 - dy1 * dx2) / (n1 * n2)
            if (cross > sinTol) bends++
        }
        return bends
    }

    private fun fmt(v: Double): String = "%.3f".format(v)

    private fun resolveTestDxf(): File {
        val candidates = listOf(
            File("test.dxf"),
            File("../test.dxf"),
            File("../../test.dxf"),
            File(System.getProperty("user.dir"), "test.dxf"),
            File(System.getProperty("user.dir"), "../test.dxf"),
        )
        return candidates.firstOrNull { it.isFile }?.canonicalFile
            ?: error("test.dxf not found cwd=${System.getProperty("user.dir")}")
    }

    @Test
    fun heatMapLayout_withWorldMapper_preservesUShapeNotHorizontal() {
        println("======== HeatMapRouteLayout WORLD vs HORIZONTAL ========")
        val u = listOf(
            Point2d(0.0, 0.0),
            Point2d(1000.0, 0.0),
            Point2d(1000.0, 500.0),
            Point2d(0.0, 500.0),
        )
        val candidates = listOf(
            RouteCandidate(
                id = "rc-0",
                layerName = "CONVEYOR",
                centerLine = u,
                length = 2500.0,
                sourcePolylineCount = 1,
                confidence = 1f,
            ),
        )
        val repo = RouteRepository()
        val gen = RouteGenerator(repo).generate(candidates, "u")!!
        val horizontal = com.example.cnv.heatmap.HeatMapRouteLayout.build(gen.route, worldMapper = null)!!
        val world = com.example.cnv.heatmap.HeatMapRouteLayout.build(gen.route, worldMapper = gen.mapper)!!

        val hYs = gen.route.segments.mapNotNull { seg ->
            com.example.cnv.heatmap.HeatMapRouteLayout.toDrawingCoordinate(horizontal, seg.id, 0f)?.y
        }
        val wYs = gen.route.segments.flatMap { seg ->
            listOfNotNull(
                com.example.cnv.heatmap.HeatMapRouteLayout.toDrawingCoordinate(world, seg.id, 0f)?.y,
                com.example.cnv.heatmap.HeatMapRouteLayout.toDrawingCoordinate(world, seg.id, 1f)?.y,
            )
        }
        println("LOG[HORIZONTAL] yValues=$hYs")
        println("LOG[WORLD] yValues=$wYs")
        val hSpan = (hYs.maxOrNull() ?: 0.0) - (hYs.minOrNull() ?: 0.0)
        val wSpan = (wYs.maxOrNull() ?: 0.0) - (wYs.minOrNull() ?: 0.0)
        println("LOG[COMPARE] horizontalYSpan=$hSpan worldYSpan=$wSpan")
        assertTrue("horizontal fallback forces ySpan≈0", hSpan < 1e-6)
        assertTrue("world mapper must keep ㄷ height", wSpan > 100.0)
    }
}
