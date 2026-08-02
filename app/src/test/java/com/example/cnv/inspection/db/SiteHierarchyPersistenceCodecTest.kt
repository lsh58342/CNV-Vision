package com.example.cnv.inspection.db

import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.RouteAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteHierarchyPersistenceCodecTest {

    @Test
    fun drawingEntity_roundTripsOriginAndDwg() {
        val drawing = Drawing(
            id = "d1",
            floorId = "f1",
            name = "Line-A",
            dwgUri = "/data/cad/plant.dxf",
            dwgRegistered = true,
            originSet = true,
            originX = 0.35f,
            originY = 0.35f,
            routeId = "r1",
            calibrationReady = true,
            routeLocked = false,
        )
        val restored = SiteDrawingEntity.from(drawing).toModel()
        assertEquals(drawing.id, restored.id)
        assertEquals(drawing.dwgUri, restored.dwgUri)
        assertTrue(restored.originSet)
        assertEquals(0.35f, restored.originX!!, 0.0001f)
        assertEquals("r1", restored.routeId)
        assertTrue(restored.calibrationReady)
    }

    @Test
    fun routeAnchorCodec_roundTrips() {
        val anchor = RouteAnchor(segmentId = "S0", progress = 0.42f)
        val decoded = RouteAnchorCodec.decode(RouteAnchorCodec.encode(anchor))
        assertEquals("S0", decoded.segmentId)
        assertEquals(0.42f, decoded.progress!!, 0.0001f)
    }
}
