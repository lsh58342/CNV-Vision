package com.example.cnv.inspection.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Database(
    entities = [
        InspectionSessionEntity::class,
        InspectionEventEntity::class,
        ConveyorProfileEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class CnvInspectionDatabase : RoomDatabase() {
    abstract fun sessionDao(): InspectionSessionDao
    abstract fun eventDao(): InspectionEventDao
    abstract fun conveyorProfileDao(): ConveyorProfileDao

    companion object {
        private const val DB_NAME = "cnv_inspection.db"

        /** Dedicated Room query/transaction pool — never the main thread. */
        private val roomExecutor: ExecutorService = Executors.newFixedThreadPool(2)

        fun build(context: Context): CnvInspectionDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CnvInspectionDatabase::class.java,
                DB_NAME,
            )
                .setQueryExecutor(roomExecutor)
                .setTransactionExecutor(roomExecutor)
                .fallbackToDestructiveMigration()
                .build()
    }
}
