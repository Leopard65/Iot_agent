package com.example.iotgpt.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.iotgpt.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for chat messages and message-count statistics.
 */
@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC
        """
    )
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM messages
            WHERE conversationId = :conversationId
            ORDER BY createdAt DESC
            LIMIT :limit
        ) ORDER BY createdAt ASC
        """
    )
    fun observeLatestMessages(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAt ASC
        """
    )
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessage(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query(
        """
        DELETE FROM messages
        WHERE conversationId = :conversationId
          AND createdAt > :createdAt
        """
    )
    suspend fun deleteMessagesAfter(conversationId: String, createdAt: Long)

    @Query("SELECT COUNT(*) FROM messages")
    fun observeMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    fun observeMessageCountForConversation(conversationId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE role = :role")
    fun observeMessageCountByRole(role: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE createdAt >= :startOfDayMillis")
    fun observeMessageCountSince(startOfDayMillis: Long): Flow<Int>

    @Query("SELECT createdAt FROM messages WHERE createdAt >= :startMillis ORDER BY createdAt ASC")
    fun observeMessageTimesSince(startMillis: Long): Flow<List<Long>>
}
