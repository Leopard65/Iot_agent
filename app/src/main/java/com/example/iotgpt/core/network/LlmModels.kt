package com.example.iotgpt.core.network

/**
 * Minimal chat message model for OpenAI-compatible Chat Completions.
 */
data class LlmChatMessage(
    val role: String,
    val content: String,
    val imageDataUrls: List<String> = emptyList()
)

data class LlmChatResult(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

class LlmApiException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
