package com.example.iotgpt.feature.chat

import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkdownExporterTest {

    @Test
    fun export_includesTitleMetadataAndMessagesInAscendingTime() {
        val conversation = ConversationEntity(
            id = "c1",
            title = "测试对话",
            summary = "摘要",
            createdAt = 1L,
            updatedAt = 3L,
            modelId = "deepseek-chat",
            messageCount = 2
        )
        val messages = listOf(
            message(id = "m2", role = "assistant", content = "回复", createdAt = 2L),
            message(id = "m1", role = "user", content = "你好", createdAt = 1L)
        )

        val markdown = ConversationMarkdownExporter.export(conversation, messages)

        assertTrue(markdown.startsWith("# 测试对话"))
        assertTrue(markdown.contains("- 模型：deepseek-chat"))
        assertTrue(markdown.contains("- 消息数：2"))
        assertTrue(markdown.indexOf("## 用户") < markdown.indexOf("## AI 助手"))
        assertTrue(markdown.contains("你好"))
        assertTrue(markdown.contains("回复"))
    }

    @Test
    fun fileName_removesInvalidPathCharacters() {
        val fileName = ConversationMarkdownExporter.fileName(
            conversation = ConversationEntity(
                id = "c1",
                title = """a/b:c*?d""",
                summary = null,
                createdAt = 1L,
                updatedAt = 1L,
                modelId = "model",
                messageCount = 0
            )
        )

        assertTrue(fileName.startsWith("a_b_c_d_"))
        assertTrue(fileName.endsWith(".md"))
    }

    private fun message(
        id: String,
        role: String,
        content: String,
        createdAt: Long
    ): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = "c1",
            role = role,
            content = content,
            attachmentJson = null,
            createdAt = createdAt,
            isStreaming = false,
            tokenCount = null
        )
    }
}
