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
    private val dedupe = NotificationDedupe()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (dedupe.seen(sbn.key, sbn.postTime)) return
        val extras = sbn.notification.extras
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
        val repo = (application as NftListenerApp).repository
        scope.launch { repo.handleIncoming(fields) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
