package com.example.iotgpt.core.network

/**
 * Minimal chat message model for OpenAI-compatible Chat Completions.
 */
data class LlmChatMessage(
    val role: String,
    val content: String,
    val imageDataUrls: List<String> = emptyList()
)

data class LlmAudioInput(
    val fileName: String,
    val mimeType: String?,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlmAudioInput) return false
        return fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class LlmChatResult(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

data class LlmChatStreamChunk(
    val delta: String = "",
    val finishReason: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

class LlmApiException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
