package com.example.cnv.dwg

import com.example.cnv.route.RouteExtractor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pilot verification against a real DXF on disk (repo root test.dxf).
 * Prints step logs — not Build Success alone.
 */
class DxfPilotVerificationTest {

    @Test
    fun verifyRealDxfPipeline() {
        val dxf = resolveTestDxf()
        println("LOG[FILE] path=${dxf.absolutePath} size=${dxf.length()} exists=${dxf.isFile}")

        // 1) DXF Open
        val reader = DxfReader()
        val opened = runCatching { reader.open(dxf.absolutePath) }
        println("LOG[1_OPEN] success=${opened.isSuccess} error=${opened.exceptionOrNull()?.message}")
        assertTrue("DXF open must succeed", opened.isSuccess)
        val document = opened.getOrThrow()
        println("LOG[1_OPEN] fileName=${document.fileName} sourcePath=${document.sourcePath}")

        // 2) Layers
        val layers = reader.readLayers(document)
        println("LOG[2_LAYER] count=${layers.size} names=${layers.map { it.name }}")

        // 3–4) Entities / Polylines
        val bag = reader.readEntities(document)
        val entityTotal =
            bag.polylines.size + bag.lines.size + bag.arcs.size +
                bag.circles.size + bag.texts.size + bag.blocks.size
        println(
            "LOG[3_ENTITY] total=$entityTotal " +
                "polylines=${bag.polylines.size} lines=${bag.lines.size} " +
                "arcs=${bag.arcs.size} circles=${bag.circles.size} " +
                "texts=${bag.texts.size} blocks=${bag.blocks.size}",
        )
        println("LOG[4_POLYLINE] count=${bag.polylines.size}")
        bag.polylines.forEachIndexed { i, pl ->
            println(
                "LOG[4_POLYLINE][$i] id=${pl.id} layer=${pl.layerName} " +
                    "pts=${pl.points.size} closed=${pl.closed} length=${pl.length()}",
            )
        }
        bag.lines.forEachIndexed { i, ln ->
            println(
                "LOG[3_ENTITY][LINE][$i] layer=${ln.layerName} " +
                    "start=${ln.start} end=${ln.end} length=${ln.length()}",
            )
        }

        // 5) GeometryExtractor
        val geometry = GeometryExtractor().extract(document.fileName, layers, bag)
        println(
            "LOG[5_GEOMETRY] file=${geometry.fileName} layers=${geometry.layers.size} " +
                "polylines=${geometry.polylines.size} lines=${geometry.lines.size} " +
                "arcs=${geometry.arcs.size} circles=${geometry.circles.size} " +
                "texts=${geometry.texts.size}",
        )

        // 6) RouteCandidate — default CONVEYOR + actual layers present
        val extractor = RouteExtractor()
        val conveyor = extractor.extract(geometry, DWGConfig.DEFAULT_LAYER_FILTER)
        println(
            "LOG[6_ROUTE][CONVEYOR] selected=${conveyor.selectedLayer} " +
                "sourcePolylines=${conveyor.sourcePolylines.size} " +
                "merged=${conveyor.mergedPolylines.size} " +
                "centerLines=${conveyor.centerLines.size} " +
                "candidates=${conveyor.candidates.size}",
        )

        layers.map { it.name }.distinct().forEach { layerName ->
            val r = extractor.extract(geometry, layerName)
            println(
                "LOG[6_ROUTE][$layerName] sourcePolylines=${r.sourcePolylines.size} " +
                    "merged=${r.mergedPolylines.size} candidates=${r.candidates.size}",
            )
            r.candidates.forEachIndexed { i, c ->
                println(
                    "LOG[6_ROUTE][$layerName][cand$i] id=${c.id} " +
                        "pts=${c.centerLine.size} length=${c.length} conf=${c.confidence}",
                )
            }
        }

        // Full importer path (same as app Route generation)
        val importer = DWGImporter(reader = DxfReader())
        val importDefault = importer.importFrom(dxf.absolutePath)
        println(
            "LOG[6_IMPORT][default CONVEYOR] candidates=${importDefault.routeCandidateCount} " +
                "polylineCount=${importDefault.polylineCount} " +
                "selectedLayer=${importDefault.selectedLayer}",
        )
        val importLayer0 = importer.importFrom(dxf.absolutePath, layerName = "0")
        println(
            "LOG[6_IMPORT][layer 0] candidates=${importLayer0.routeCandidateCount} " +
                "polylineCount=${importLayer0.polylineCount} " +
                "selectedLayer=${importLayer0.selectedLayer}",
        )

        println("LOG[7_CAD_VIEWER] SKIPPED no connected device/emulator (adb unavailable)")
        println("LOG[8_ZOOM] SKIPPED no connected device/emulator")
        println("LOG[9_PAN] SKIPPED no connected device/emulator")
        println("LOG[10_ORIGIN] SKIPPED no connected device/emulator")
        println("LOG[11_CALIBRATION] SKIPPED no connected device/emulator")
        println("LOG[12_INSPECTION] SKIPPED no connected device/emulator")
    }

    private fun resolveTestDxf(): File {
        val candidates = listOf(
            File("test.dxf"),
            File("../test.dxf"),
            File("../../test.dxf"),
            File("../../../test.dxf"),
            File(System.getProperty("user.dir"), "test.dxf"),
            File(System.getProperty("user.dir"), "../test.dxf"),
            File(System.getProperty("user.dir"), "../../test.dxf"),
        )
        val found = candidates.firstOrNull { it.isFile }
            ?: error("test.dxf not found; cwd=${System.getProperty("user.dir")}")
        return found.canonicalFile
    }
}
