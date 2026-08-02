package com.example.cnv.factory.repository

import com.example.cnv.factory.model.Building
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.Factory
import com.example.cnv.factory.model.Floor
import com.example.cnv.factory.model.Zone
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.RouteSnapshot
import com.example.cnv.inspection.RouteSnapshotCodec
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.inspection.db.SiteBuildingEntity
import com.example.cnv.inspection.db.SiteCalibrationEntity
import com.example.cnv.inspection.db.SiteDrawingEntity
import com.example.cnv.inspection.db.SiteDrawingRouteEntity
import com.example.cnv.inspection.db.SiteFactoryEntity
import com.example.cnv.inspection.db.SiteFloorEntity
import com.example.cnv.inspection.db.SiteHierarchySnapshot
import com.example.cnv.inspection.db.SiteZoneEntity
import com.example.cnv.map.Route

/**
 * Persists Factory site hierarchy via Room (STEP 20-10 P0).
 * Runtime cache stays in FactoryCatalog memory repositories.
 */
object SitePersistenceRepository {

    fun saveFactoryAsync(factory: Factory) {
        InspectionDbGate.execute {
            dao()?.upsertFactory(SiteFactoryEntity.from(factory))
        }
    }

    fun saveBuildingAsync(building: Building) {
        InspectionDbGate.execute {
            dao()?.upsertBuilding(SiteBuildingEntity.from(building))
        }
    }

    fun saveFloorAsync(floor: Floor) {
        InspectionDbGate.execute {
            dao()?.upsertFloor(SiteFloorEntity.from(floor))
        }
    }

    fun saveDrawingAsync(drawing: Drawing) {
        InspectionDbGate.execute {
            dao()?.upsertDrawing(SiteDrawingEntity.from(drawing))
        }
    }

    fun saveZoneAsync(zone: Zone) {
        InspectionDbGate.execute {
            dao()?.upsertZone(SiteZoneEntity.from(zone))
        }
    }

    fun saveCalibrationAsync(ref: CalibrationRepository.CalibrationRef) {
        InspectionDbGate.execute {
            dao()?.upsertCalibration(SiteCalibrationEntity.from(ref))
        }
    }

    fun saveDrawingRouteAsync(drawingId: String, route: Route) {
        val json = RouteSnapshotCodec.encode(RouteSnapshot.from(route))
        InspectionDbGate.execute {
            dao()?.upsertDrawingRoute(SiteDrawingRouteEntity(drawingId = drawingId, routeJson = json))
        }
    }

    fun deleteBuildingAsync(id: String) {
        InspectionDbGate.execute { dao()?.deleteBuilding(id) }
    }

    fun deleteFloorAsync(id: String) {
        InspectionDbGate.execute { dao()?.deleteFloor(id) }
    }

    fun deleteDrawingCascadeAsync(drawingId: String) {
        InspectionDbGate.execute {
            val d = dao() ?: return@execute
            d.deleteZonesForDrawing(drawingId)
            d.deleteCalibration(drawingId)
            d.deleteDrawingRoute(drawingId)
            d.deleteDrawing(drawingId)
        }
    }

    fun deleteZoneAsync(id: String) {
        InspectionDbGate.execute { dao()?.deleteZone(id) }
    }

    fun loadAllAsync(onResult: (SiteHierarchySnapshot) -> Unit) {
        InspectionDbGate.submit(
            block = {
                dao()?.loadSnapshot() ?: SiteHierarchySnapshot(
                    factories = emptyList(),
                    buildings = emptyList(),
                    floors = emptyList(),
                    drawings = emptyList(),
                    zones = emptyList(),
                    calibrations = emptyList(),
                    routesByDrawingId = emptyMap(),
                )
            },
            onMain = onResult,
        )
    }

    fun decodeRoute(json: String): Route? =
        RouteSnapshotCodec.decode(json)?.toRoute()

    private fun dao() = InspectionRepository.database()?.siteHierarchyDao()
}
