package com.example.cnv.inspection.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.example.cnv.factory.model.Building
import com.example.cnv.factory.model.Drawing
import com.example.cnv.factory.model.Factory
import com.example.cnv.factory.model.Floor
import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.factory.model.Zone
import com.example.cnv.factory.repository.CalibrationRepository
import org.json.JSONObject

/**
 * Site hierarchy Room tables (STEP 20-10 P0 persistence).
 * FactoryCatalog memory maps remain the runtime cache.
 */

@Entity(tableName = "site_factories")
data class SiteFactoryEntity(
    @PrimaryKey val id: String,
    val name: String,
) {
    fun toModel() = Factory(id = id, name = name)

    companion object {
        fun from(model: Factory) = SiteFactoryEntity(id = model.id, name = model.name)
    }
}

@Entity(tableName = "site_buildings")
data class SiteBuildingEntity(
    @PrimaryKey val id: String,
    val factoryId: String,
    val name: String,
) {
    fun toModel() = Building(id = id, factoryId = factoryId, name = name)

    companion object {
        fun from(model: Building) =
            SiteBuildingEntity(id = model.id, factoryId = model.factoryId, name = model.name)
    }
}

@Entity(tableName = "site_floors")
data class SiteFloorEntity(
    @PrimaryKey val id: String,
    val buildingId: String,
    val name: String,
) {
    fun toModel() = Floor(id = id, buildingId = buildingId, name = name)

    companion object {
        fun from(model: Floor) =
            SiteFloorEntity(id = model.id, buildingId = model.buildingId, name = model.name)
    }
}

@Entity(tableName = "site_drawings")
data class SiteDrawingEntity(
    @PrimaryKey val id: String,
    val floorId: String,
    val name: String,
    val dwgUri: String? = null,
    val description: String = "",
    val registeredAtMs: Long = 0L,
    val dwgRegistered: Boolean = false,
    val conveyorLayerName: String = "CONVEYOR",
    val originSet: Boolean = false,
    val originX: Float? = null,
    val originY: Float? = null,
    val routeId: String? = null,
    val routeLocked: Boolean = false,
    val calibrationReady: Boolean = false,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
) {
    fun toModel(): Drawing = Drawing(
        id = id,
        floorId = floorId,
        name = name,
        dwgUri = dwgUri,
        description = description,
        registeredAtMs = registeredAtMs,
        dwgRegistered = dwgRegistered,
        conveyorLayerName = conveyorLayerName,
        originSet = originSet,
        originX = originX,
        originY = originY,
        routeId = routeId,
        routeLocked = routeLocked,
        calibrationReady = calibrationReady,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )

    companion object {
        fun from(model: Drawing) = SiteDrawingEntity(
            id = model.id,
            floorId = model.floorId,
            name = model.name,
            dwgUri = model.dwgUri,
            description = model.description,
            registeredAtMs = model.registeredAtMs,
            dwgRegistered = model.dwgRegistered,
            conveyorLayerName = model.conveyorLayerName,
            originSet = model.originSet,
            originX = model.originX,
            originY = model.originY,
            routeId = model.routeId,
            routeLocked = model.routeLocked,
            calibrationReady = model.calibrationReady,
            createdAtMs = model.createdAtMs,
            updatedAtMs = model.updatedAtMs,
        )
    }
}

@Entity(tableName = "site_zones")
data class SiteZoneEntity(
    @PrimaryKey val id: String,
    val drawingId: String,
    val routeId: String,
    val name: String,
    val colorLabel: String,
    val colorArgb: Int,
    val startJson: String,
    val endJson: String,
    val polylineIdsJson: String = "",
    val calibrationVersion: Int? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
) {
    fun toModel(): Zone = Zone(
        id = id,
        drawingId = drawingId,
        routeId = routeId,
        name = name,
        colorLabel = colorLabel,
        colorArgb = colorArgb,
        start = RouteAnchorCodec.decode(startJson),
        end = RouteAnchorCodec.decode(endJson),
        polylineIds = PolylineIdsCodec.decode(polylineIdsJson),
        calibrationVersion = calibrationVersion,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )

    companion object {
        fun from(model: Zone) = SiteZoneEntity(
            id = model.id,
            drawingId = model.drawingId,
            routeId = model.routeId,
            name = model.name,
            colorLabel = model.colorLabel,
            colorArgb = model.colorArgb,
            startJson = RouteAnchorCodec.encode(model.start),
            endJson = RouteAnchorCodec.encode(model.end),
            polylineIdsJson = PolylineIdsCodec.encode(model.polylineIds),
            calibrationVersion = model.calibrationVersion,
            createdAtMs = model.createdAtMs,
            updatedAtMs = model.updatedAtMs,
        )
    }
}

@Entity(tableName = "site_calibrations")
data class SiteCalibrationEntity(
    @PrimaryKey val drawingId: String,
    val calibrationVersion: Int,
    val mmPerPixel: Float? = null,
    val ready: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    fun toModel() = CalibrationRepository.CalibrationRef(
        drawingId = drawingId,
        calibrationVersion = calibrationVersion,
        mmPerPixel = mmPerPixel,
        ready = ready,
        updatedAtMs = updatedAtMs,
    )

    companion object {
        fun from(model: CalibrationRepository.CalibrationRef) = SiteCalibrationEntity(
            drawingId = model.drawingId,
            calibrationVersion = model.calibrationVersion,
            mmPerPixel = model.mmPerPixel,
            ready = model.ready,
            updatedAtMs = model.updatedAtMs,
        )
    }
}

@Entity(tableName = "site_drawing_routes")
data class SiteDrawingRouteEntity(
    @PrimaryKey val drawingId: String,
    val routeJson: String,
)

object RouteAnchorCodec {
    private const val SEP = "\u0001"

    fun encode(anchor: RouteAnchor): String = listOf(
        anchor.nodeId.orEmpty(),
        anchor.segmentId.orEmpty(),
        anchor.distanceFromSegmentStartMm?.toString().orEmpty(),
        anchor.progress?.toString().orEmpty(),
    ).joinToString(SEP)

    fun decode(raw: String): RouteAnchor {
        if (raw.isBlank()) return RouteAnchor()
        // Legacy JSON (if any) — best-effort empty; new format is delimiter-based.
        if (raw.trimStart().startsWith("{")) {
            return runCatching {
                val o = JSONObject(raw)
                RouteAnchor(
                    nodeId = o.optString("nodeId").takeIf { it.isNotBlank() && it != "null" },
                    segmentId = o.optString("segmentId").takeIf { it.isNotBlank() && it != "null" },
                    distanceFromSegmentStartMm = if (o.has("distanceFromSegmentStartMm") && !o.isNull("distanceFromSegmentStartMm")) {
                        o.getDouble("distanceFromSegmentStartMm").toFloat()
                    } else {
                        null
                    },
                    progress = if (o.has("progress") && !o.isNull("progress")) {
                        o.getDouble("progress").toFloat()
                    } else {
                        null
                    },
                )
            }.getOrDefault(RouteAnchor())
        }
        val parts = raw.split(SEP)
        return RouteAnchor(
            nodeId = parts.getOrNull(0)?.takeIf { it.isNotBlank() },
            segmentId = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
            distanceFromSegmentStartMm = parts.getOrNull(2)?.toFloatOrNull(),
            progress = parts.getOrNull(3)?.toFloatOrNull(),
        )
    }
}

object PolylineIdsCodec {
    private const val SEP = "\u0001"

    fun encode(ids: List<String>): String =
        ids.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(SEP)

    fun decode(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }
    }
}

data class SiteHierarchySnapshot(
    val factories: List<Factory>,
    val buildings: List<Building>,
    val floors: List<Floor>,
    val drawings: List<Drawing>,
    val zones: List<Zone>,
    val calibrations: List<CalibrationRepository.CalibrationRef>,
    val routesByDrawingId: Map<String, String>,
)

@Dao
interface SiteHierarchyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFactory(entity: SiteFactoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBuilding(entity: SiteBuildingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertFloor(entity: SiteFloorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDrawing(entity: SiteDrawingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertZone(entity: SiteZoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCalibration(entity: SiteCalibrationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDrawingRoute(entity: SiteDrawingRouteEntity)

    @Query("SELECT * FROM site_factories")
    fun allFactories(): List<SiteFactoryEntity>

    @Query("SELECT * FROM site_buildings")
    fun allBuildings(): List<SiteBuildingEntity>

    @Query("SELECT * FROM site_floors")
    fun allFloors(): List<SiteFloorEntity>

    @Query("SELECT * FROM site_drawings")
    fun allDrawings(): List<SiteDrawingEntity>

    @Query("SELECT * FROM site_zones")
    fun allZones(): List<SiteZoneEntity>

    @Query("SELECT * FROM site_calibrations")
    fun allCalibrations(): List<SiteCalibrationEntity>

    @Query("SELECT * FROM site_drawing_routes")
    fun allDrawingRoutes(): List<SiteDrawingRouteEntity>

    @Query("DELETE FROM site_buildings WHERE id = :id")
    fun deleteBuilding(id: String)

    @Query("DELETE FROM site_floors WHERE id = :id")
    fun deleteFloor(id: String)

    @Query("DELETE FROM site_drawings WHERE id = :id")
    fun deleteDrawing(id: String)

    @Query("DELETE FROM site_zones WHERE drawingId = :drawingId")
    fun deleteZonesForDrawing(drawingId: String)

    @Query("DELETE FROM site_zones WHERE id = :id")
    fun deleteZone(id: String)

    @Query("DELETE FROM site_calibrations WHERE drawingId = :drawingId")
    fun deleteCalibration(drawingId: String)

    @Query("DELETE FROM site_drawing_routes WHERE drawingId = :drawingId")
    fun deleteDrawingRoute(drawingId: String)

    @Transaction
    fun loadSnapshot(): SiteHierarchySnapshot {
        return SiteHierarchySnapshot(
            factories = allFactories().map { it.toModel() },
            buildings = allBuildings().map { it.toModel() },
            floors = allFloors().map { it.toModel() },
            drawings = allDrawings().map { it.toModel() },
            zones = allZones().map { it.toModel() },
            calibrations = allCalibrations().map { it.toModel() },
            routesByDrawingId = allDrawingRoutes().associate { it.drawingId to it.routeJson },
        )
    }
}
