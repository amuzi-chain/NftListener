package com.amz.nftlistener.domain

data class NotificationFields(
    val eventId: String,
    val notificationKey: String,
    val postedAt: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val subText: String,
    val channelId: String,
    val isOngoing: Boolean,
)
