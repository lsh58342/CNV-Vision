package com.example.cnv.inspection.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Drawing-scoped Inspection Profile persistence (STEP 19-2).
 * Conveyor lives primarily on Drawing / ConveyorProfileEntity; sensor/rule/export JSON here.
 */
@Entity(tableName = "inspection_profiles")
data class InspectionProfileEntity(
    @PrimaryKey
    val drawingId: String,
    val profileJson: String,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

@Dao
interface InspectionProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: InspectionProfileEntity)

    @Query("SELECT * FROM inspection_profiles WHERE drawingId = :drawingId LIMIT 1")
    fun get(drawingId: String): InspectionProfileEntity?

    @Query("SELECT * FROM inspection_profiles")
    fun all(): List<InspectionProfileEntity>

    @Query("DELETE FROM inspection_profiles WHERE drawingId = :drawingId")
    fun delete(drawingId: String)
}
