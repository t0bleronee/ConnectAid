package com.flydrop2p.flydrop2p.data.local.outbox

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiverAccountId: Long,
    val messageId: Long, // local message id saved in MessageEntity
    val type: String, // TEXT only for now
    val payloadText: String?,
    val createdAt: Long,
    val retryCount: Int,
    val nextAttemptAt: Long
)


