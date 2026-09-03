package com.amz.nftlistener.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadLogDao {
    @Insert
    suspend fun insert(entity: UploadLogEntity)

    @Query("UPDATE upload_logs SET status = :status, reason = :reason WHERE eventId = :eventId")
    suspend fun updateByEventId(eventId: String, status: String, reason: String)

    @Query(
        "DELETE FROM upload_logs WHERE id NOT IN (SELECT id FROM upload_logs ORDER BY loggedAt DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    @Query("SELECT * FROM upload_logs ORDER BY loggedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<UploadLogEntity>>
}
