package com.example.iotgpt.core.network

import com.example.iotgpt.core.preferences.LlmSettings

/**
 * Contract for OpenAI-compatible large language model requests.
 */
interface LlmApiService {
    suspend fun createChatCompletion(
        settings: LlmSettings,
        messages: List<LlmChatMessage>,
        temperature: Double = 0.7
    ): LlmChatResult
}
