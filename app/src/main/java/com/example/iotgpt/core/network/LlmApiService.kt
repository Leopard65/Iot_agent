package com.example.iotgpt.core.network

import com.example.iotgpt.core.preferences.LlmSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Contract for OpenAI-compatible large language model requests.
 */
interface LlmApiService {
    suspend fun createChatCompletion(
        settings: LlmSettings,
        messages: List<LlmChatMessage>,
        temperature: Double = 0.7
    ): LlmChatResult

    suspend fun transcribeAudio(
        settings: LlmSettings,
        audio: LlmAudioInput
    ): String {
        throw LlmApiException("当前服务未实现语音转写")
    }

    fun streamChatCompletion(
        settings: LlmSettings,
        messages: List<LlmChatMessage>,
        temperature: Double = 0.7
    ): Flow<LlmChatStreamChunk> = flow {
        val result = createChatCompletion(settings, messages, temperature)
        emit(
            LlmChatStreamChunk(
                delta = result.content,
                finishReason = "stop",
                promptTokens = result.promptTokens,
                completionTokens = result.completionTokens,
                totalTokens = result.totalTokens
            )
        )
    }
}
