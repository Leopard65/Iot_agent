package com.example.iotgpt.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.iotgpt.core.database.dao.AgentTaskDao
import com.example.iotgpt.core.database.dao.ConversationDao
import com.example.iotgpt.core.database.dao.MessageDao
import com.example.iotgpt.core.database.dao.ModelUsageDao
import com.example.iotgpt.core.database.entity.AgentTaskEntity
import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.core.database.entity.ModelUsageEntity

/**
 * Room database for conversations, messages, model usage and local agent tasks.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ModelUsageEntity::class,
        AgentTaskEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun modelUsageDao(): ModelUsageDao
    abstract fun agentTaskDao(): AgentTaskDao

    companion object {
        private const val DATABASE_NAME = "iotgpt.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Returns the process-wide database instance.
         */
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE model_usages ADD COLUMN isEstimated INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
