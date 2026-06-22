package com.example.iotgpt.feature.chat.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.ui.theme.LotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageBubbleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun streamingAssistantMessage_withPartialContent_displaysContentAndThinkingState() {
        setBubble(
            MessageEntity(
                id = "assistant-streaming",
                conversationId = "conversation",
                role = "assistant",
                content = "正在流式输出第一段内容",
                attachmentJson = null,
                createdAt = 1_700_000_000_000L,
                isStreaming = true,
                tokenCount = null
            )
        )

        composeRule.onNodeWithText("正在流式输出第一段内容").assertIsDisplayed()
        composeRule.onNodeWithText("AI 助手 · 思考中").assertIsDisplayed()
        composeRule.onNodeWithText("测试模型 · 思考中").assertIsDisplayed()
    }

    @Test
    fun streamingAssistantMessage_withoutContent_displaysThinkingState() {
        setBubble(
            MessageEntity(
                id = "assistant-empty-streaming",
                conversationId = "conversation",
                role = "assistant",
                content = "",
                attachmentJson = null,
                createdAt = 1_700_000_000_000L,
                isStreaming = true,
                tokenCount = null
            )
        )

        composeRule.onNodeWithText("AI 助手 · 思考中").assertIsDisplayed()
        composeRule.onNodeWithText("测试模型 · 思考中").assertIsDisplayed()
    }

    private fun setBubble(message: MessageEntity) {
        composeRule.setContent {
            LotTheme(darkTheme = false, dynamicColor = false) {
                MessageBubble(
                    message = message,
                    showHeader = true,
                    modelLabel = "测试模型",
                    reduceMotion = true,
                    onCopy = {},
                    onRetry = {}
                )
            }
        }
    }
}
