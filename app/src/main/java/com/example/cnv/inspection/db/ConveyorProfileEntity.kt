package com.example.cnv.inspection.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.cnv.factory.model.ConveyorDirection
import com.example.cnv.factory.model.ConveyorMotionProfile
import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.factory.model.ConveyorProfileConfig

/**
 * Persisted Conveyor Profile per Drawing (STEP 15-4).
 */
@Entity(tableName = "conveyor_profiles")
data class ConveyorProfileEntity(
    @PrimaryKey
    val drawingId: String,
    val nominalSpeedMPerMin: Float? = null,
    val speedTolerancePercent: Float = ConveyorProfileConfig.DEFAULT_SPEED_TOLERANCE_PERCENT,
    val direction: String = ConveyorDirection.FORWARD.name,
    val expectedFps: Float = ConveyorProfileConfig.DEFAULT_EXPECTED_FPS,
    val motionProfile: String = ConveyorMotionProfile.CONSTANT.name,
    val lastUpdatedMs: Long = 0L,
) {
    fun toModel(): ConveyorProfile = ConveyorProfile(
        nominalSpeedMPerMin = nominalSpeedMPerMin,
        speedTolerancePercent = speedTolerancePercent,
        direction = runCatching { ConveyorDirection.valueOf(direction) }
            .getOrDefault(ConveyorDirection.FORWARD),
        expectedFps = expectedFps,
        motionProfile = runCatching { ConveyorMotionProfile.valueOf(motionProfile) }
            .getOrDefault(ConveyorMotionProfile.CONSTANT),
        lastUpdatedMs = lastUpdatedMs,
    )

    companion object {
        fun from(drawingId: String, profile: ConveyorProfile) = ConveyorProfileEntity(
            drawingId = drawingId,
            nominalSpeedMPerMin = profile.nominalSpeedMPerMin,
            speedTolerancePercent = profile.speedTolerancePercent,
            direction = profile.direction.name,
            expectedFps = profile.expectedFps,
            motionProfile = profile.motionProfile.name,
            lastUpdatedMs = profile.lastUpdatedMs,
        )
    }
}

@Dao
interface ConveyorProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: ConveyorProfileEntity)

    @Query("SELECT * FROM conveyor_profiles WHERE drawingId = :drawingId LIMIT 1")
    fun get(drawingId: String): ConveyorProfileEntity?

    @Query("SELECT * FROM conveyor_profiles")
    fun all(): List<ConveyorProfileEntity>

    @Query("DELETE FROM conveyor_profiles WHERE drawingId = :drawingId")
    fun delete(drawingId: String)
}
