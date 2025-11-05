package com.flydrop2p.flydrop2p.data.local.outbox

class OutboxRepository(private val dao: OutboxDAO) {
    suspend fun enqueueText(receiverAccountId: Long, messageId: Long, text: String) {
        val now = System.currentTimeMillis()
        val entity = OutboxEntity(
            receiverAccountId = receiverAccountId,
            messageId = messageId,
            type = "TEXT",
            payloadText = text,
            createdAt = now,
            retryCount = 0,
            nextAttemptAt = now
        )
        dao.insert(entity)
    }

    suspend fun getDue(now: Long): List<OutboxEntity> = dao.getDue(now)
    suspend fun bumpRetry(id: Long, nextAttemptAt: Long) = dao.bumpRetry(id, nextAttemptAt)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}


