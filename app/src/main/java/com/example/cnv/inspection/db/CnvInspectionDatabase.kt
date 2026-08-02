package com.example.cnv.inspection.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [InspectionSessionEntity::class, InspectionEventEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CnvInspectionDatabase : RoomDatabase() {
    abstract fun sessionDao(): InspectionSessionDao
    abstract fun eventDao(): InspectionEventDao

    companion object {
        private const val DB_NAME = "cnv_inspection.db"

        fun build(context: Context): CnvInspectionDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CnvInspectionDatabase::class.java,
                DB_NAME,
            ).fallbackToDestructiveMigration()
                .build()
    }
}
