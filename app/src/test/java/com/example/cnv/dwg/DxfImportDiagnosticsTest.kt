package com.example.cnv.dwg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * STEP 20-9 — Import Summary / Validation / Diagnostics (memory-only).
 */
class DxfImportDiagnosticsTest {

    @Test
    fun analyze_insertSample_producesSummaryAndValidation() {
        val dxf = resolveResource("insert_sample.dxf")
        val report = DxfImportAnalyzer.analyze(dxf.absolutePath, conveyorLayer = "0")

        assertNotNull(report)
        assertEquals("insert_sample.dxf", report.summary.fileName)
        assertTrue(report.summary.dxfVersion.isNotBlank())
        assertTrue(report.summary.layerCount > 0)
        assertTrue(report.summary.layerNames.isNotEmpty())
        assertTrue(report.validation.hasLayers)
        assertTrue(report.validation.conveyorLayerExists)
        assertTrue(report.validation.hasGeometry)
        assertFalse(report.validation.emptyDrawing)
        assertTrue(report.validation.boundingBoxOk)
        assertTrue(report.validation.calibrationGeometryOk)
        assertTrue(report.validation.originSelectable)
        assertEquals(DxfImportDiagnosticsStore.latest, report)

        val message = report.userFacingMessage()
        assertTrue(message.contains("Import Summary"))
        assertTrue(message.contains("File Name:"))
    }

    @Test
    fun analyze_missingConveyorLayer_emitsWarningAndGuidance() {
        val dxf = resolveResource("insert_sample.dxf")
        val report = DxfImportAnalyzer.analyze(dxf.absolutePath, conveyorLayer = "NO_SUCH_LAYER")

        assertFalse(report.validation.conveyorLayerExists)
        assertTrue(report.validation.warnings.any { it.contains("Conveyor Layer") })
        assertTrue(report.validation.guidance.any { it.contains("Conveyor Layer를 선택") })
        assertTrue(
            report.status == DxfImportStatus.WARNING ||
                report.status == DxfImportStatus.ERROR,
        )
    }

    @Test
    fun analyze_repoTestDxf_logsDeveloperSummaries() {
        val dxf = resolveRepoTestDxf() ?: return
        val report = DxfImportAnalyzer.analyze(dxf.absolutePath, conveyorLayer = "0")
        assertTrue(report.summary.layerCount >= 1)
        assertTrue(report.validation.hasLayers)
        // Default pilot drawing often has no CONVEYOR layer → warning expected when using CONVEYOR.
        val conveyorReport = DxfImportAnalyzer.analyze(dxf.absolutePath, conveyorLayer = "CONVEYOR")
        assertTrue(conveyorReport.validation.warnings.isNotEmpty() || conveyorReport.validation.errors.isNotEmpty())
    }

    private fun resolveResource(name: String): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(name)) {
            "Missing test resource $name"
        }
        return File(url.toURI())
    }

    private fun resolveRepoTestDxf(): File? {
        val candidates = listOf(
            File("test.dxf"),
            File("../test.dxf"),
            File("../../test.dxf"),
            File("app/../test.dxf"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
