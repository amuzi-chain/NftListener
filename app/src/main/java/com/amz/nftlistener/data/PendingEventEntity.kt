package com.amz.nftlistener.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_events")
data class PendingEventEntity(
    @PrimaryKey val eventId: String,
    val postedAt: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val payloadJson: String,
    val createdAt: Long,
)
