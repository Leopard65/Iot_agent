package com.example.iotgpt.feature.chat

import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Chat data contract for local conversation persistence.
 */
interface ChatRepository {
    fun observeConversations(): Flow<List<ConversationEntity>>
    fun observeMessages(conversationId: String?): Flow<List<MessageEntity>>
    fun observeMessageCount(conversationId: String?): Flow<Int>
    val visibleMessageLimit: StateFlow<Int>
    suspend fun loadOlderMessages(conversationId: String)
    suspend fun resetMessagePagination()
    suspend fun createConversation(): ConversationEntity
    suspend fun deleteConversation(id: String)
    suspend fun renameConversation(id: String, title: String)
    suspend fun exportConversationMarkdown(conversationId: String): ConversationMarkdownExport
    suspend fun sendUserMessage(conversationId: String?, content: String): String
    suspend fun sendAttachmentMessage(
        conversationId: String?,
        content: String,
        attachmentJson: String
    ): String
    suspend fun addLocalMessage(
        conversationId: String?,
        role: String,
        content: String,
        attachmentJson: String? = null
    ): String
    suspend fun stopStreamingAssistant(conversationId: String?)
    suspend fun regenerateLastAssistant(conversationId: String): String
    suspend fun retryAssistantMessage(messageId: String): String
}

data class ConversationMarkdownExport(
    val fileName: String,
    val markdown: String
)
