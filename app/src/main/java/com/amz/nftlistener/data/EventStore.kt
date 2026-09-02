package com.amz.nftlistener.data

import kotlinx.coroutines.flow.Flow

interface EventStore {
    suspend fun insertPending(entity: PendingEventEntity)
    suspend fun pendingCount(): Int
    suspend fun oldestPending(): PendingEventEntity?
    suspend fun deletePending(eventId: String)
    suspend fun allPendingOldestFirst(): List<PendingEventEntity>
    suspend fun insertLog(entity: UploadLogEntity)
    suspend fun updateLogByEventId(eventId: String, status: String, reason: String)
    suspend fun trimLogs(keep: Int = 200)
    fun observeRecentLogs(limit: Int = 200): Flow<List<UploadLogEntity>>
}
