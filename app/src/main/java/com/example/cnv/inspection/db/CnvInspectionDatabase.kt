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
    ],
    version = 7,
    exportSchema = false,
)
abstract class CnvInspectionDatabase : RoomDatabase() {
    abstract fun sessionDao(): InspectionSessionDao
    abstract fun eventDao(): InspectionEventDao
    abstract fun conveyorProfileDao(): ConveyorProfileDao
    abstract fun inspectionProfileDao(): InspectionProfileDao

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

        fun build(context: Context): CnvInspectionDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CnvInspectionDatabase::class.java,
                DB_NAME,
            )
                .setQueryExecutor(roomExecutor)
                .setTransactionExecutor(roomExecutor)
                .addMigrations(MIGRATION_6_7)
                .build()
    }
}
