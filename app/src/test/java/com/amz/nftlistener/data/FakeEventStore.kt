package com.amz.nftlistener.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEventStore : EventStore {
    val pending = mutableListOf<PendingEventEntity>()
    val logs = mutableListOf<UploadLogEntity>()
    private val logFlow = MutableStateFlow<List<UploadLogEntity>>(emptyList())
    private var nextLogId = 1L

    override suspend fun insertPending(entity: PendingEventEntity) {
        pending.add(entity)
    }

    override suspend fun pendingCount(): Int = pending.size

    override suspend fun oldestPending(): PendingEventEntity? =
        pending.minByOrNull { it.createdAt }

    override suspend fun deletePending(eventId: String) {
        pending.removeAll { it.eventId == eventId }
    }

    override suspend fun allPendingOldestFirst(): List<PendingEventEntity> =
        pending.sortedBy { it.createdAt }

    override suspend fun insertLog(entity: UploadLogEntity) {
        val stored = if (entity.id == 0L) entity.copy(id = nextLogId++) else entity
        logs.add(stored)
        publish()
    }

    override suspend fun updateLogByEventId(eventId: String, status: String, reason: String) {
        val index = logs.indexOfLast { it.eventId == eventId }
        if (index >= 0) {
            logs[index] = logs[index].copy(status = status, reason = reason)
            publish()
        }
    }

    override suspend fun trimLogs(keep: Int) {
        if (logs.size > keep) {
            logs.sortBy { it.loggedAt }
            while (logs.size > keep) {
                logs.removeAt(0)
            }
            publish()
        }
    }

    override fun observeRecentLogs(limit: Int): Flow<List<UploadLogEntity>> = logFlow

    private fun publish() {
        logFlow.value = logs.sortedByDescending { it.loggedAt }.take(200)
    }
}
