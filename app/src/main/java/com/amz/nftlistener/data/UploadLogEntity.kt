package com.amz.nftlistener.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_logs")
data class UploadLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val postedAt: Long,
    val appLabel: String,
    val title: String,
    val status: String,
    val reason: String,
    val loggedAt: Long,
)
