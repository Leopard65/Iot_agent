package com.example.iotgpt.feature.chat

import com.example.iotgpt.core.database.entity.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ContextWindowSelector] — verifies token-budget-aware message
 * windowing used to build the LLM context.
 */
class ContextWindowSelectorTest {

    @Test
    fun emptyInput_returnsEmpty() {
        assertEquals(emptyList<MessageEntity>(), ContextWindowSelector.select(emptyList()))
    }

    @Test
    fun singleMessage_alwaysKept_evenIfOverTokenLimit() {
        val messages = listOf(makeMessage(id = "1", content = "x".repeat(10000), tokenCount = 5000))
        val result = ContextWindowSelector.select(messages, maxTokens = 100)
        assertEquals(1, result.size)
        assertEquals("1", result.first().id)
    }

    @Test
    fun multipleMessages_withinTokenLimit_keepsAll() {
        val messages = (1..5).map { i ->
            makeMessage(id = "$i", content = "msg$i", tokenCount = 100)
        }
        val result = ContextWindowSelector.select(messages, maxTokens = 1000)
        assertEquals(5, result.size)
        // Order preserved: oldest first
        assertEquals(listOf("1", "2", "3", "4", "5"), result.map { it.id })
    }

    @Test
    fun tokenOverflow_truncatesOldest() {
        // 3 messages each 500 tokens; budget = 1200 → newest 2 fit, oldest dropped
        val messages = (1..3).map { i ->
            makeMessage(id = "$i", content = "msg$i", tokenCount = 500)
        }
        val result = ContextWindowSelector.select(messages, maxTokens = 1200)
        assertEquals(2, result.size)
        assertEquals(listOf("2", "3"), result.map { it.id })
    }

    @Test
    fun maxMessageCap_respected() {
        val messages = (1..100).map { i ->
            makeMessage(id = "$i", content = "msg$i", tokenCount = 1)
        }
        val result = ContextWindowSelector.select(messages, maxMessages = 40, maxTokens = 10000)
        assertEquals(40, result.size)
        // Newest 40: IDs 61..100
        assertEquals("61", result.first().id)
        assertEquals("100", result.last().id)
    }

    @Test
    fun longMessage_alwaysKeepsAtLeastOne() {
        // One huge message that exceeds token budget
        val messages = listOf(
            makeMessage(id = "1", content = "small", tokenCount = 10),
            makeMessage(id = "2", content = "huge", tokenCount = 9999)
        )
        val result = ContextWindowSelector.select(messages, maxTokens = 100)
        // The newest message (id=2) should be kept even though it exceeds budget
        assertEquals(1, result.size)
        assertEquals("2", result.first().id)
    }

    @Test
    fun nullTokenCount_usesEstimate() {
        val messages = listOf(
            makeMessage(id = "1", content = "a".repeat(200), tokenCount = null), // ~100 tokens
            makeMessage(id = "2", content = "b".repeat(200), tokenCount = null)  // ~100 tokens
        )
        val result = ContextWindowSelector.select(messages, maxTokens = 150)
        // Only the newest fits within 150 tokens
        assertEquals(1, result.size)
        assertEquals("2", result.first().id)
    }

    @Test
    fun estimateTokenCount_minOne() {
        assertEquals(1, ContextWindowSelector.estimateTokenCount(""))
        assertEquals(1, ContextWindowSelector.estimateTokenCount("a"))
        assertEquals(5, ContextWindowSelector.estimateTokenCount("abcdefghij"))
    }

    @Test
    fun preservesOrder_afterReverseSelection() {
        val messages = (1..5).map { i ->
            makeMessage(id = "$i", content = "msg$i", tokenCount = 100)
        }
        val result = ContextWindowSelector.select(messages, maxTokens = 350)
        // Newest 3: IDs 3, 4, 5 — in original order
        assertEquals(listOf("3", "4", "5"), result.map { it.id })
    }

    private fun makeMessage(
        id: String,
        content: String = "content",
        role: String = "user",
        tokenCount: Int? = null
    ): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = "conv-1",
            role = role,
            content = content,
            attachmentJson = null,
            createdAt = id.toLongOrNull() ?: 0L,
            isStreaming = false,
            tokenCount = tokenCount
        )
    }
}
