package com.flydrop2p.flydrop2p.data.local.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface OutboxDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OutboxEntity): Long

    @Delete
    suspend fun delete(item: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE nextAttemptAt <= :now ORDER BY createdAt ASC LIMIT 50")
    suspend fun getDue(now: Long): List<OutboxEntity>

    @Query("UPDATE outbox SET retryCount = retryCount + 1, nextAttemptAt = :nextAttemptAt WHERE id = :id")
    suspend fun bumpRetry(id: Long, nextAttemptAt: Long)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: Long)
}


