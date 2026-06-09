package com.example.iotgpt.feature.chat

import android.content.Context
import com.example.iotgpt.core.database.AppDatabase
import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.core.database.entity.ModelUsageEntity
import com.example.iotgpt.core.network.LlmApiService
import com.example.iotgpt.core.network.LlmChatMessage
import com.example.iotgpt.core.network.LlmChatResult
import com.example.iotgpt.core.network.OpenAiCompatibleClient
import com.example.iotgpt.core.notification.NotificationHelper
import com.example.iotgpt.core.preferences.LlmSettings
import com.example.iotgpt.core.preferences.SettingsStore
import com.example.iotgpt.core.util.FileUtils
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Room-backed chat repository that persists user and assistant messages.
 */
class ChatRepositoryImpl(
    database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val appContext: Context? = null,
    private val llmApiService: LlmApiService = OpenAiCompatibleClient(),
    private val notificationHelper: NotificationHelper? = null
) : ChatRepository {
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val modelUsageDao = database.modelUsageDao()

    override fun observeConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.observeConversations()
    }

    override fun observeMessages(conversationId: String?): Flow<List<MessageEntity>> {
        return if (conversationId == null) {
            flowOf(emptyList())
        } else {
            messageDao.observeMessages(conversationId)
        }
    }

    override suspend fun createConversation(): ConversationEntity {
        val now = System.currentTimeMillis()
        val activeModel = settingsStore.activeModelProfile.first().model
        val conversation = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "新会话",
            summary = "本地草稿",
            createdAt = now,
            updatedAt = now,
            modelId = activeModel,
            messageCount = 0
        )
        conversationDao.upsertConversation(conversation)
        return conversation
    }

    override suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversationById(id)
    }

    override suspend fun sendUserMessage(conversationId: String?, content: String): String {
        return sendMessage(
            conversationId = conversationId,
            content = content,
            attachmentJson = null
        )
    }

    override suspend fun sendAttachmentMessage(
        conversationId: String?,
        content: String,
        attachmentJson: String
    ): String {
        return sendMessage(
            conversationId = conversationId,
            content = content,
            attachmentJson = attachmentJson
        )
    }

    private suspend fun sendMessage(
        conversationId: String?,
        content: String,
        attachmentJson: String?
    ): String {
        val conversation = conversationId
            ?.let { conversationDao.getConversation(it) }
            ?: createConversation()
        val now = System.currentTimeMillis()

        messageDao.upsertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversation.id,
                role = "user",
                content = content,
                attachmentJson = attachmentJson,
                createdAt = now,
                isStreaming = false,
                tokenCount = estimateTokenCount(content)
            )
        )

        val messages = messageDao.getMessages(conversation.id)
        refreshConversationMeta(conversation.id, messages, now)

        val settings = settingsStore.settings.first()
        return requestAndPersistAssistant(conversation.id, settings)
    }

    override suspend fun regenerateLastAssistant(conversationId: String): String {
        val messages = messageDao.getMessages(conversationId)
        val lastUserMessage = messages.lastOrNull { it.role == "user" }
            ?: throw IllegalStateException("没有可重新生成的用户消息")

        messageDao.deleteMessagesAfter(conversationId, lastUserMessage.createdAt)
        refreshConversationMeta(
            conversationId = conversationId,
            messages = messageDao.getMessages(conversationId),
            updatedAt = System.currentTimeMillis()
        )

        val settings = settingsStore.settings.first()
        return requestAndPersistAssistant(conversationId, settings)
    }

    override suspend fun retryAssistantMessage(messageId: String): String {
        val failedMessage = messageDao.getMessage(messageId)
            ?: throw IllegalStateException("找不到需要重试的消息")
        if (failedMessage.role != "assistant") {
            throw IllegalStateException("只能重试 AI 回复")
        }

        messageDao.deleteMessageById(messageId)
        refreshConversationMeta(
            conversationId = failedMessage.conversationId,
            messages = messageDao.getMessages(failedMessage.conversationId),
            updatedAt = System.currentTimeMillis()
        )

        val settings = settingsStore.settings.first()
        return requestAndPersistAssistant(failedMessage.conversationId, settings)
    }

    private suspend fun requestAndPersistAssistant(
        conversationId: String,
        settings: LlmSettings
    ): String {
        val assistantMessageId = UUID.randomUUID().toString()
        val loadingMessage = MessageEntity(
            id = assistantMessageId,
            conversationId = conversationId,
            role = "assistant",
            content = "",
            attachmentJson = null,
            createdAt = System.currentTimeMillis(),
            isStreaming = true,
            tokenCount = null
        )
        messageDao.upsertMessage(loadingMessage)
        refreshConversationMeta(
            conversationId = conversationId,
            messages = messageDao.getMessages(conversationId),
            updatedAt = loadingMessage.createdAt
        )

        val assistantMessage = requestAssistantMessage(
            conversationId = conversationId,
            messageId = assistantMessageId,
            createdAt = loadingMessage.createdAt,
            settings = settings
        )
        messageDao.upsertMessage(assistantMessage)
        val finalMessages = messageDao.getMessages(conversationId)
        val summary = maybeGenerateSummaryIfNeeded(
            conversationId = conversationId,
            messages = finalMessages,
            settings = settings
        )
        refreshConversationMeta(
            conversationId = conversationId,
            messages = finalMessages,
            updatedAt = assistantMessage.createdAt,
            summaryOverride = summary
        )
        if (!assistantMessage.content.startsWith("API 请求失败")) {
            notificationHelper?.showAssistantReply(
                conversationId = conversationId,
                title = buildConversationTitle(finalMessages),
                content = assistantMessage.content
            )
        }

        return conversationId
    }

    private suspend fun requestAssistantMessage(
        conversationId: String,
        messageId: String,
        createdAt: Long,
        settings: LlmSettings
    ): MessageEntity {
        val now = System.currentTimeMillis()
        val messages = messageDao.getMessages(conversationId)
        val requestMessages = messages
            .filter { (it.role == "user" || it.role == "assistant") && !it.isStreaming }
            .filter { it.content.isNotBlank() }
            .takeLast(MAX_CONTEXT_MESSAGES)
            .map { it.toLlmChatMessage(settings) }

        val result = runCatching {
            llmApiService.createChatCompletion(
                settings = settings,
                messages = requestMessages
            )
        }

        val chatResult = result.getOrNull()
        if (chatResult != null) {
            settingsStore.saveLastApiError(null)
            recordModelUsage(
                modelId = settings.model,
                conversationId = conversationId,
                result = chatResult,
                requestMessages = requestMessages,
                createdAt = now
            )
            return MessageEntity(
                id = messageId,
                conversationId = conversationId,
                role = "assistant",
                content = chatResult.content,
                attachmentJson = null,
                createdAt = createdAt,
                isStreaming = false,
                tokenCount = chatResult.completionTokens ?: estimateTokenCount(chatResult.content)
            )
        }

        val readableError = readableApiError(
            result.exceptionOrNull() ?: IllegalStateException("API 请求失败")
        )
        settingsStore.saveLastApiError(readableError)
        return MessageEntity(
            id = messageId,
            conversationId = conversationId,
            role = "assistant",
            content = readableError,
            attachmentJson = null,
            createdAt = createdAt,
            isStreaming = false,
            tokenCount = null
        )
    }

    private suspend fun refreshConversationMeta(
        conversationId: String,
        messages: List<MessageEntity>,
        updatedAt: Long,
        summaryOverride: String? = null
    ) {
        conversationDao.updateConversationMeta(
            id = conversationId,
            title = buildConversationTitle(messages),
            summary = summaryOverride
                ?: conversationDao.getConversation(conversationId)?.summary
                ?: buildLocalSummary(messages),
            updatedAt = updatedAt,
            messageCount = messages.size
        )
    }

    private suspend fun maybeGenerateSummaryIfNeeded(
        conversationId: String,
        messages: List<MessageEntity>,
        settings: LlmSettings
    ): String? {
        val stableMessages = messages.filter { !it.isStreaming && it.content.isNotBlank() }
        if (!shouldRefreshSummary(stableMessages.size)) {
            return null
        }

        val transcript = stableMessages
            .takeLast(MAX_SUMMARY_MESSAGES)
            .joinToString("\n") { "${it.role}: ${it.content}" }
        val prompt = "请用不超过40个中文字符总结下面这段 AIoT 会话，直接输出摘要，不要解释：\n$transcript"

        return runCatching {
            val result = llmApiService.createChatCompletion(
                settings = settings,
                messages = listOf(LlmChatMessage(role = "user", content = prompt)),
                temperature = 0.2
            )
            recordModelUsage(
                modelId = settings.model,
                conversationId = conversationId,
                result = result,
                requestMessages = listOf(LlmChatMessage(role = "user", content = prompt)),
                createdAt = System.currentTimeMillis()
            )
            result.content.take(48)
        }.getOrElse {
            buildLocalSummary(stableMessages)
        }
    }

    private fun shouldRefreshSummary(messageCount: Int): Boolean {
        return messageCount >= 4 && (messageCount == 4 || (messageCount - 4) % 8 == 0)
    }

    private suspend fun recordModelUsage(
        modelId: String,
        conversationId: String,
        result: LlmChatResult,
        requestMessages: List<LlmChatMessage>,
        createdAt: Long
    ) {
        val promptTokens = result.promptTokens ?: estimateTokenCount(
            requestMessages.joinToString("\n") { it.content }
        )
        val completionTokens = result.completionTokens ?: estimateTokenCount(result.content)
        val totalTokens = result.totalTokens ?: (promptTokens + completionTokens)

        modelUsageDao.insertUsage(
            ModelUsageEntity(
                id = UUID.randomUUID().toString(),
                modelId = modelId,
                conversationId = conversationId,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                createdAt = createdAt
            )
        )
    }

    private fun readableApiError(error: Throwable): String {
        val message = error.message?.takeIf { it.isNotBlank() }
            ?: "请检查网络、API Key、Base URL 和模型名称"
        return if (message.startsWith("API") || message.startsWith("模型")) {
            message
        } else {
            "API 请求失败：$message"
        }
    }

    private fun buildConversationTitle(messages: List<MessageEntity>): String {
        val firstUserMessage = messages.firstOrNull { it.role == "user" }?.content.orEmpty()
        return firstUserMessage
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.take(18)
            ?.ifBlank { null }
            ?: "新会话"
    }

    private fun buildLocalSummary(messages: List<MessageEntity>): String {
        val firstUserMessage = messages.firstOrNull { it.role == "user" }?.content.orEmpty()
        return firstUserMessage
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(48)
            .ifBlank { "本地会话" }
    }

    private fun estimateTokenCount(content: String): Int {
        return (content.length / 2).coerceAtLeast(1)
    }

    private fun MessageEntity.toLlmChatMessage(settings: LlmSettings): LlmChatMessage {
        val attachment = FileUtils.parseAttachmentJson(attachmentJson)
        val imageDataUrl = if (settings.supportsVision && appContext != null) {
            FileUtils.readImageDataUrlIfPossible(appContext, attachment)
        } else {
            null
        }
        val fallbackNote = if (attachment?.type == "image" && imageDataUrl == null) {
            if (settings.supportsVision) {
                "\n\n提示：图片无法读取或超过 4MB，已按附件说明发送。"
            } else {
                "\n\n提示：当前模型配置未开启图片输入，图片已按附件说明发送。"
            }
        } else {
            ""
        }

        return LlmChatMessage(
            role = role,
            content = content + fallbackNote,
            imageDataUrls = listOfNotNull(imageDataUrl)
        )
    }

    private companion object {
        const val MAX_CONTEXT_MESSAGES = 20
        const val MAX_SUMMARY_MESSAGES = 12
    }
}
