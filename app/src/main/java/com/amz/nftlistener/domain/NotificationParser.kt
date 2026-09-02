package com.amz.nftlistener.domain

object NotificationParser {
    fun parse(
        eventId: String,
        notificationKey: String,
        postedAt: Long,
        packageName: String,
        appLabel: String,
        extrasTitle: CharSequence?,
        extrasText: CharSequence?,
        extrasSubText: CharSequence?,
        extrasBigText: CharSequence?,
        channelId: String?,
        isOngoing: Boolean,
    ): NotificationFields {
        val rawText = extrasText?.toString().orEmpty().ifEmpty {
            extrasBigText?.toString().orEmpty()
        }
        return NotificationFields(
            eventId = eventId,
            notificationKey = notificationKey,
            postedAt = postedAt,
            packageName = packageName,
            appLabel = appLabel.ifBlank { packageName },
            title = TextTruncator.truncate(extrasTitle?.toString().orEmpty()),
            text = TextTruncator.truncate(rawText),
            subText = TextTruncator.truncate(extrasSubText?.toString().orEmpty()),
            channelId = channelId.orEmpty(),
            isOngoing = isOngoing,
        )
    }
}
