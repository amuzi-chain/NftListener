package com.amz.nftlistener.data

import com.amz.nftlistener.domain.NotificationFields
import com.amz.nftlistener.domain.NotificationParser
import com.amz.nftlistener.domain.PayloadJson
import com.amz.nftlistener.domain.UploadOutcome
import com.amz.nftlistener.settings.AppSettings
import com.amz.nftlistener.upload.WebhookSender
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NotificationRepository(
    private val store: EventStore,
    private val settings: AppSettings,
    private val sender: WebhookSender,
    private val networkChecker: NetworkChecker,
    private val scheduler: UploadScheduler,
) {
    val logs: Flow<List<UploadLogEntity>> = store.observeRecentLogs(200)

    suspend fun handleIncoming(fields: NotificationFields) {
        if (!settings.isConfigured()) {
            insertLog(fields, LogStatus.FAILED, "未配置")
            store.trimLogs(200)
            return
        }
        if (store.pendingCount() >= MAX_PENDING) {
            val oldest = store.oldestPending()
            if (oldest != null) {
                store.deletePending(oldest.eventId)
                store.insertLog(
                    UploadLogEntity(
                        eventId = oldest.eventId,
                        postedAt = oldest.postedAt,
                        appLabel = oldest.appLabel,
                        title = oldest.title,
                        status = LogStatus.FAILED.name,
                        reason = "队列溢出",
                        loggedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        store.insertPending(
            PendingEventEntity(
                eventId = fields.eventId,
                postedAt = fields.postedAt,
                packageName = fields.packageName,
                appLabel = fields.appLabel,
                title = fields.title,
                payloadJson = PayloadJson.encode(fields),
                createdAt = System.currentTimeMillis(),
            ),
        )
        insertLog(fields, LogStatus.QUEUED, "")
        store.trimLogs(200)
        if (!networkChecker.isOnline()) {
            scheduler.schedule()
            return
        }
        applyOutcome(
            fields.eventId,
            sender.upload(settings.webhookUrl(), settings.token(), PayloadJson.encode(fields)),
        )
    }

    suspend fun enqueueTestEvent(now: Long = System.currentTimeMillis()) {
        val fields = NotificationParser.parse(
            eventId = UUID.randomUUID().toString(),
            notificationKey = "test|$now",
            postedAt = now,
            packageName = "com.amz.nftlistener",
            appLabel = "NftListener",
            extrasTitle = "NftListener test",
            extrasText = "This is a test webhook payload",
            extrasSubText = "",
            extrasBigText = null,
            channelId = "test",
            isOngoing = false,
        )
        handleIncoming(fields)
    }

    suspend fun drainPending(): DrainResult {
        if (!settings.isConfigured()) return DrainResult.DONE
        var hasRetryable = false
        for (event in store.allPendingOldestFirst()) {
            val drainOutcome = applyOutcome(
                event.eventId,
                sender.upload(settings.webhookUrl(), settings.token(), event.payloadJson),
            )
            if (drainOutcome == DrainResult.HAS_RETRYABLE) {
                hasRetryable = true
            }
        }
        return if (hasRetryable) DrainResult.HAS_RETRYABLE else DrainResult.DONE
    }

    private suspend fun applyOutcome(eventId: String, outcome: UploadOutcome): DrainResult {
        return when (outcome) {
            UploadOutcome.Success -> {
                store.deletePending(eventId)
                store.updateLogByEventId(eventId, LogStatus.SUCCESS.name, "")
                DrainResult.DONE
            }
            is UploadOutcome.Permanent -> {
                store.deletePending(eventId)
                store.updateLogByEventId(eventId, LogStatus.FAILED.name, outcome.reason)
                DrainResult.DONE
            }
            is UploadOutcome.Retryable -> {
                scheduler.schedule()
                DrainResult.HAS_RETRYABLE
            }
        }
    }

    private suspend fun insertLog(fields: NotificationFields, status: LogStatus, reason: String) {
        store.insertLog(
            UploadLogEntity(
                eventId = fields.eventId,
                postedAt = fields.postedAt,
                appLabel = fields.appLabel,
                title = fields.title,
                status = status.name,
                reason = reason,
                loggedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val MAX_PENDING = 1000
    }
}
