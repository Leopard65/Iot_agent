package com.example.iotgpt.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iotgpt.core.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for chat conversations.
 */
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Delete
    suspend fun deleteConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversationById(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearConversations()

    @Query(
        """
        UPDATE conversations
        SET title = :title,
            summary = :summary,
            updatedAt = :updatedAt,
            messageCount = :messageCount
        WHERE id = :id
        """
    )
    suspend fun updateConversationMeta(
        id: String,
        title: String,
        summary: String?,
        updatedAt: Long,
        messageCount: Int
    )

    @Query(
        """
        UPDATE conversations
        SET title = :title,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun renameConversation(
        id: String,
        title: String,
        updatedAt: Long
    )

    @Query("SELECT COUNT(*) FROM conversations")
    fun observeConversationCount(): Flow<Int>
}
