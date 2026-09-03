package com.amz.nftlistener.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingEventEntity)

    @Query("SELECT COUNT(*) FROM pending_events")
    suspend fun count(): Int

    @Query("SELECT * FROM pending_events ORDER BY createdAt ASC LIMIT 1")
    suspend fun oldest(): PendingEventEntity?

    @Query("DELETE FROM pending_events WHERE eventId = :eventId")
    suspend fun deleteById(eventId: String)

    @Query("SELECT * FROM pending_events ORDER BY createdAt ASC")
    suspend fun allOldestFirst(): List<PendingEventEntity>
}
