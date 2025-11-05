package com.flydrop2p.flydrop2p.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.flydrop2p.flydrop2p.data.local.account.AccountDAO
import com.flydrop2p.flydrop2p.data.local.account.AccountEntity
import com.flydrop2p.flydrop2p.data.local.message.MessageDAO
import com.flydrop2p.flydrop2p.data.local.message.MessageEntity
import com.flydrop2p.flydrop2p.data.local.profile.ProfileDAO
import com.flydrop2p.flydrop2p.data.local.outbox.OutboxDAO
import com.flydrop2p.flydrop2p.data.local.outbox.OutboxEntity
import com.flydrop2p.flydrop2p.data.local.profile.ProfileEntity


@Database(entities = [AccountEntity::class, MessageEntity::class, ProfileEntity::class, OutboxEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): AccountDAO

    abstract fun messageDao(): MessageDAO

    abstract fun profileDao(): ProfileDAO

    abstract fun outboxDao(): OutboxDAO

    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "Flydrop2p Database"
                ).fallbackToDestructiveMigration().build()
                Instance = instance
                instance
            }
        }
    }

}