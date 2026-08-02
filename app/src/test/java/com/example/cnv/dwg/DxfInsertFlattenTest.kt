package com.example.cnv.dwg

import com.example.cnv.route.RouteExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * STEP 20-8 — BLOCK / INSERT flatten verification.
 */
class DxfInsertFlattenTest {

    @Test
    fun flattenInsertAppliesTranslateRotateScale() {
        val dxf = resolveInsertSample()
        println("LOG[FILE] path=${dxf.absolutePath} size=${dxf.length()}")

        val reader = DxfReader()
        val doc = reader.open(dxf.absolutePath)
        val layers = reader.readLayers(doc)
        val bag = reader.readEntities(doc)
        val stats = reader.lastStats

        println("LOG[BLOCKS] definitions=${stats.blockDefinitionCount}")
        println("LOG[INSERT] count=${stats.insertCount} flattened=${stats.flattenedFromInsert}")
        println("LOG[LAYER] names=${layers.map { it.name }}")
        println(
            "LOG[GEOMETRY] lines=${bag.lines.size} polylines=${bag.polylines.size} " +
                "arcs=${bag.arcs.size} circles=${bag.circles.size} texts=${bag.texts.size}",
        )

        assertTrue("Must read BLOCK definitions", stats.blockDefinitionCount >= 1)
        assertEquals("Must find one INSERT", 1, stats.insertCount)
        assertTrue("INSERT must flatten geometry", stats.flattenedFromInsert >= 3)

        // INSERT at (1000,500), rot 90°, scale 2:
        // LINE (0,0)->(100,0) → (1000,500)->(1000,700)
        val insertLines = bag.lines.filter { it.id.startsWith("ins-") }
        println("LOG[TRANSFORM] insertLines=${insertLines.size}")
        insertLines.forEachIndexed { i, ln ->
            println("LOG[TRANSFORM][LINE][$i] layer=${ln.layerName} ${ln.start} -> ${ln.end}")
        }
        assertTrue(insertLines.isNotEmpty())
        val first = insertLines.first { absEq(it.start.x, 1000.0) && absEq(it.start.y, 500.0) }
        assertTrue("Rotated/scaled end Y should be ~700", absEq(first.end.x, 1000.0))
        assertTrue("Rotated/scaled end Y should be ~700", absEq(first.end.y, 700.0))
        assertEquals("Layer 0 in block inherits INSERT layer", "CONVEYOR", first.layerName)

        val insertCircles = bag.circles.filter { it.id.startsWith("ins-") }
        assertTrue(insertCircles.isNotEmpty())
        val cir = insertCircles.first()
        println("LOG[TRANSFORM][CIRCLE] center=${cir.center} r=${cir.radius} layer=${cir.layerName}")
        // Circle at (50,0) * scale2 * rot90 → (1000,600), r=10
        assertTrue(absEq(cir.center.x, 1000.0))
        assertTrue(absEq(cir.center.y, 600.0))
        assertTrue(absEq(cir.radius, 10.0))

        val geometry = GeometryExtractor().extract(doc.fileName, layers, bag)
        val route = RouteExtractor().extract(geometry, "CONVEYOR")
        println(
            "LOG[ROUTE] candidates=${route.candidates.size} " +
                "sourcePolylines=${route.sourcePolylines.size}",
        )
        assertTrue("CONVEYOR layer should yield route candidates", route.candidates.isNotEmpty())
    }

    @Test
    fun factoryDxfDoesNotDumpUnreferencedBlocks() {
        val dxf = resolveRepoTestDxf()
        val reader = DxfReader()
        val doc = reader.open(dxf.absolutePath)
        val bag = reader.readEntities(doc)
        val stats = reader.lastStats
        println(
            "LOG[test.dxf] blocks=${stats.blockDefinitionCount} inserts=${stats.insertCount} " +
                "flattened=${stats.flattenedFromInsert} lines=${bag.lines.size} " +
                "polylines=${bag.polylines.size} circles=${bag.circles.size}",
        )
        assertEquals(0, stats.insertCount)
        assertEquals(0, stats.flattenedFromInsert)
        // Without INSERT, annotation blocks must not appear as model geometry.
        assertTrue(bag.polylines.none { it.id.startsWith("blk-") || it.id.startsWith("ins-") })
        assertTrue(bag.circles.none { it.id.startsWith("blk-") || it.id.startsWith("ins-") })
        assertEquals(3, bag.lines.size)
    }

    private fun absEq(a: Double, b: Double, eps: Double = 1e-6): Boolean = kotlin.math.abs(a - b) <= eps

    private fun resolveInsertSample(): File {
        val candidates = listOf(
            File("src/test/resources/insert_sample.dxf"),
            File("app/src/test/resources/insert_sample.dxf"),
            File("../app/src/test/resources/insert_sample.dxf"),
        )
        return candidates.firstOrNull { it.isFile }?.canonicalFile
            ?: error("insert_sample.dxf not found; cwd=${System.getProperty("user.dir")}")
    }

    private fun resolveRepoTestDxf(): File {
        val candidates = listOf(
            File("test.dxf"),
            File("../test.dxf"),
            File("../../test.dxf"),
        )
        return candidates.firstOrNull { it.isFile }?.canonicalFile
            ?: error("test.dxf not found; cwd=${System.getProperty("user.dir")}")
    }
}
