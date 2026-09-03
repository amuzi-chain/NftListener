package com.amz.nftlistener.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.amz.nftlistener.NftListenerApp
import com.amz.nftlistener.domain.NotificationDedupe
import com.amz.nftlistener.domain.NotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationCaptureService : NotificationListenerService() {
    companion object {
        var isConnected = false
            private set(value) {
                field = value
                // 这里可以扩展更复杂的监听逻辑
            }
    }

    private val dedupe = NotificationDedupe()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        val active = runCatching { activeNotifications?.size }.getOrNull()
        CaptureLog.i("listener connected, activeNotifications=$active")
    }

    override fun onListenerDisconnected() {
        isConnected = false
        CaptureLog.w("listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 尝试让服务更持久
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        CaptureLog.i(
            "posted id=${sbn.id} key=${sbn.key} postTime=${sbn.postTime} " +
                "pkg=${sbn.packageName} channel=${sbn.notification.channelId} " +
                "ongoing=${sbn.isOngoing} clearable=${sbn.isClearable} " +
                "group=${sbn.groupKey} extrasKeys=${extras.keySet()} " +
                "rawTitle=${extras.getCharSequence(Notification.EXTRA_TITLE)} " +
                "rawText=${extras.getCharSequence(Notification.EXTRA_TEXT)} " +
                "rawSubText=${extras.getCharSequence(Notification.EXTRA_SUB_TEXT)} " +
                "rawBigText=${extras.getCharSequence(Notification.EXTRA_BIG_TEXT)} " +
                "rawInfoText=${extras.getCharSequence(Notification.EXTRA_INFO_TEXT)} " +
                "rawSummary=${extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)}",
        )
        if (dedupe.seen(sbn.key, sbn.postTime)) {
            CaptureLog.i("skip duplicate key=${sbn.key} postTime=${sbn.postTime}")
            return
        }
        val packageName = sbn.packageName.orEmpty()
        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        val fields = NotificationParser.parse(
            eventId = UUID.randomUUID().toString(),
            notificationKey = sbn.key,
            postedAt = sbn.postTime,
            packageName = packageName,
            appLabel = appLabel,
            extrasTitle = extras.getCharSequence(Notification.EXTRA_TITLE),
            extrasText = extras.getCharSequence(Notification.EXTRA_TEXT),
            extrasSubText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            extrasBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            channelId = sbn.notification.channelId,
            isOngoing = sbn.isOngoing,
        )
        CaptureLog.fields(fields)
        val repo = (application as NftListenerApp).repository
        scope.launch { repo.handleIncoming(fields) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        CaptureLog.i("removed key=${sbn.key} pkg=${sbn.packageName} id=${sbn.id}")
    }

    override fun onDestroy() {
        CaptureLog.i("capture service destroyed")
        scope.cancel()
        super.onDestroy()
    }
}
