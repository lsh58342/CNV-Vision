package com.example.cnv.factory.seed

import android.graphics.Color
import com.example.cnv.factory.model.Building
import com.example.cnv.factory.model.Factory
import com.example.cnv.factory.model.Floor
import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.CalibrationRepository
import com.example.cnv.factory.repository.FactoryCatalog

/**
 * In-memory demo hierarchy for Operation / Commissioning navigation.
 */
object FactorySeedData {

    const val FACTORY_ID = "factory-demo-1"
    const val BUILDING_WA1 = "building-wa1"
    const val BUILDING_WA2 = "building-wa2"
    const val BUILDING_WA3 = "building-wa3"
    const val FLOOR_1F = "floor-wa1-1f"
    const val FLOOR_2F = "floor-wa1-2f"
    const val FLOOR_3F = "floor-wa1-3f"
    const val ROUTE_DEMO = "route-demo-1"
    const val ZONE_A = "zone-wa1-1f-a"
    const val ZONE_B = "zone-wa1-1f-b"

    @Volatile
    private var seeded = false

    fun ensureSeeded(catalog: FactoryCatalog = FactoryCatalog.get()) {
        if (seeded) return
        synchronized(this) {
            if (seeded) return
            catalog.factories.upsert(Factory(FACTORY_ID, "Demo Factory"))
            catalog.buildings.upsert(Building(BUILDING_WA1, FACTORY_ID, "WA1"))
            catalog.buildings.upsert(Building(BUILDING_WA2, FACTORY_ID, "WA2"))
            catalog.buildings.upsert(Building(BUILDING_WA3, FACTORY_ID, "WA3"))
            catalog.floors.upsert(Floor(FLOOR_1F, BUILDING_WA1, "1F"))
            catalog.floors.upsert(Floor(FLOOR_2F, BUILDING_WA1, "2F"))
            catalog.floors.upsert(Floor(FLOOR_3F, BUILDING_WA1, "3F"))
            catalog.zones.upsert(
                Zone(
                    id = ZONE_A,
                    floorId = FLOOR_1F,
                    routeId = ROUTE_DEMO,
                    name = "Conveyor Zone A",
                    colorLabel = "Red",
                    colorArgb = Color.parseColor("#E53935"),
                    start = RouteAnchor(nodeId = "N1"),
                    end = RouteAnchor(nodeId = "N3"),
                    calibrationVersion = 1,
                    dwgRegistered = true,
                ),
            )
            catalog.zones.upsert(
                Zone(
                    id = ZONE_B,
                    floorId = FLOOR_1F,
                    routeId = ROUTE_DEMO,
                    name = "Conveyor Zone B",
                    colorLabel = "Blue",
                    colorArgb = Color.parseColor("#1E88E5"),
                    start = RouteAnchor(segmentId = "S1", distanceFromSegmentStartMm = 0f),
                    end = RouteAnchor(segmentId = "S2", progress = 1f),
                    calibrationVersion = null,
                    dwgRegistered = true,
                ),
            )
            catalog.calibrations.put(
                CalibrationRepository.CalibrationRef(
                    zoneId = ZONE_A,
                    calibrationVersion = 1,
                    mmPerPixel = 0.12f,
                    ready = true,
                ),
            )
            seeded = true
        }
    }
}
