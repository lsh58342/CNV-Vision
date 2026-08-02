package com.example.cnv.inspection.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Database(
    entities = [
        InspectionSessionEntity::class,
        InspectionEventEntity::class,
        ConveyorProfileEntity::class,
        InspectionProfileEntity::class,
        SiteFactoryEntity::class,
        SiteBuildingEntity::class,
        SiteFloorEntity::class,
        SiteDrawingEntity::class,
        SiteZoneEntity::class,
        SiteCalibrationEntity::class,
        SiteDrawingRouteEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class CnvInspectionDatabase : RoomDatabase() {
    abstract fun sessionDao(): InspectionSessionDao
    abstract fun eventDao(): InspectionEventDao
    abstract fun conveyorProfileDao(): ConveyorProfileDao
    abstract fun inspectionProfileDao(): InspectionProfileDao
    abstract fun siteHierarchyDao(): SiteHierarchyDao

    companion object {
        private const val DB_NAME = "cnv_inspection.db"

        /** Dedicated Room query/transaction pool — never the main thread. */
        private val roomExecutor: ExecutorService = Executors.newFixedThreadPool(2)

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE inspection_sessions ADD COLUMN routeSnapshotJson TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE inspection_sessions ADD COLUMN analysisResultJson TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE inspection_sessions ADD COLUMN ruleResultJson TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE inspection_sessions ADD COLUMN heatPointsJson TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE inspection_sessions ADD COLUMN excelFileUri TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE inspection_sessions ADD COLUMN excelFileName TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS site_factories (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS site_buildings (
                        id TEXT NOT NULL PRIMARY KEY,
                        factoryId TEXT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS site_floors (
                        id TEXT NOT NULL PRIMARY KEY,
                        buildingId TEXT NOT NULL,
                        name TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS site_drawings (
                        id TEXT NOT NULL PRIMARY KEY,
                        floorId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        dwgUri TEXT,
                        description TEXT NOT NULL,
                        registeredAtMs INTEGER NOT NULL,
                        dwgRegistered INTEGER NOT NULL,
                        conveyorLayerName TEXT NOT NULL,
                        originSet INTEGER NOT NULL,
                        originX REAL,
                        originY REAL,
                        routeId TEXT,
                        routeLocked INTEGER NOT NULL,
                        calibrationReady INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS site_zones (
                        id TEXT NOT NULL PRIMARY KEY,
                        drawingId TEXT NOT NULL,
                        routeId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        colorLabel TEXT NOT NULL,
                        colorArgb INTEGER NOT NULL,
                        startJson TEXT NOT NULL,
                        endJson TEXT NOT NULL,
                        calibrationVersion INTEGER,
                        createdAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS site_calibrations (
                        drawingId TEXT NOT NULL PRIMARY KEY,
                        calibrationVersion INTEGER NOT NULL,
                        mmPerPixel REAL,
                        ready INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS site_drawing_routes (
                        drawingId TEXT NOT NULL PRIMARY KEY,
                        routeJson TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): CnvInspectionDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CnvInspectionDatabase::class.java,
                DB_NAME,
            )
                .setQueryExecutor(roomExecutor)
                .setTransactionExecutor(roomExecutor)
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .build()
    }
}
