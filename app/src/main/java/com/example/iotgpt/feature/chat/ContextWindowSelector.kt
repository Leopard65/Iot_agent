package com.example.iotgpt.feature.chat

import com.example.iotgpt.core.database.entity.MessageEntity

/**
 * Selects the most recent messages that fit within a token budget, walking from
 * newest to oldest. Always keeps at least one message and never exceeds the hard
 * message cap; prefers each message's persisted [MessageEntity.tokenCount],
 * falling back to a length-based estimate.
 */
internal object ContextWindowSelector {
    const val MAX_CONTEXT_MESSAGES = 40
    const val MAX_CONTEXT_TOKENS = 3000

    fun select(
        eligible: List<MessageEntity>,
        maxMessages: Int = MAX_CONTEXT_MESSAGES,
        maxTokens: Int = MAX_CONTEXT_TOKENS
    ): List<MessageEntity> {
        val selected = ArrayList<MessageEntity>()
        var usedTokens = 0
        for (message in eligible.asReversed()) {
            if (selected.size >= maxMessages) break
            val tokens = message.tokenCount ?: estimateTokenCount(message.content)
            if (selected.isNotEmpty() && usedTokens + tokens > maxTokens) break
            usedTokens += tokens
            selected.add(message)
        }
        return selected.asReversed()
    }

    fun estimateTokenCount(content: String): Int {
        return (content.length / 2).coerceAtLeast(1)
    }
}
