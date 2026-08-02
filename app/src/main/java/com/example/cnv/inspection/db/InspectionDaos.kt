package com.example.cnv.inspection.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface InspectionSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: InspectionSessionEntity)

    @Update
    fun updateSession(session: InspectionSessionEntity)

    @Query("SELECT * FROM inspection_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun getSession(sessionId: String): InspectionSessionEntity?

    @Query(
        """
        SELECT * FROM inspection_sessions
        WHERE drawingId = :drawingId AND finished = 1
        ORDER BY endTimeMs DESC
        """,
    )
    fun historyForDrawing(drawingId: String): List<InspectionSessionEntity>

    @Query("DELETE FROM inspection_sessions WHERE sessionId = :sessionId")
    fun deleteSession(sessionId: String)

    @Query("DELETE FROM inspection_sessions WHERE drawingId = :drawingId")
    fun deleteSessionsForDrawing(drawingId: String)
}

@Dao
interface InspectionEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvent(event: InspectionEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvents(events: List<InspectionEventEntity>)

    @Query("SELECT * FROM inspection_events WHERE sessionId = :sessionId ORDER BY timestampNs ASC")
    fun eventsForSession(sessionId: String): List<InspectionEventEntity>

    @Query("DELETE FROM inspection_events WHERE sessionId = :sessionId")
    fun deleteEventsForSession(sessionId: String)

    @Query("DELETE FROM inspection_events WHERE drawingId = :drawingId")
    fun deleteEventsForDrawing(drawingId: String)
}
