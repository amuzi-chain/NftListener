package com.amz.nftlistener.data

import kotlinx.coroutines.flow.Flow

class RoomEventStore(private val db: AppDatabase) : EventStore {
    override suspend fun insertPending(entity: PendingEventEntity) {
        db.pendingEventDao().insert(entity)
    }

    override suspend fun pendingCount(): Int = db.pendingEventDao().count()

    override suspend fun oldestPending(): PendingEventEntity? = db.pendingEventDao().oldest()

    override suspend fun deletePending(eventId: String) {
        db.pendingEventDao().deleteById(eventId)
    }

    override suspend fun allPendingOldestFirst(): List<PendingEventEntity> =
        db.pendingEventDao().allOldestFirst()

    override suspend fun insertLog(entity: UploadLogEntity) {
        db.uploadLogDao().insert(entity)
    }

    override suspend fun updateLogByEventId(eventId: String, status: String, reason: String) {
        db.uploadLogDao().updateByEventId(eventId, status, reason)
    }

    override suspend fun trimLogs(keep: Int) {
        db.uploadLogDao().trim(keep)
    }

    override fun observeRecentLogs(limit: Int): Flow<List<UploadLogEntity>> =
        db.uploadLogDao().observeRecent(limit)
}
