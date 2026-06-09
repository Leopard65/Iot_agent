package com.example.iotgpt.feature.chat

import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Chat data contract for local conversation persistence.
 */
interface ChatRepository {
    fun observeConversations(): Flow<List<ConversationEntity>>
    fun observeMessages(conversationId: String?): Flow<List<MessageEntity>>
    suspend fun createConversation(): ConversationEntity
    suspend fun deleteConversation(id: String)
    suspend fun sendUserMessage(conversationId: String?, content: String): String
    suspend fun sendAttachmentMessage(
        conversationId: String?,
        content: String,
        attachmentJson: String
    ): String
    suspend fun regenerateLastAssistant(conversationId: String): String
    suspend fun retryAssistantMessage(messageId: String): String
}
