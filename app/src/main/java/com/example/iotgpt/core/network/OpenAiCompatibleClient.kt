package com.example.iotgpt.core.network

import com.example.iotgpt.core.preferences.LlmSettings
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val SYSTEM_PROMPT =
    "你是一个 AI 物联网专业智能助手，擅长解释传感器、嵌入式开发、MQTT、HTTP、CoAP、Modbus、边缘计算、设备联网、数据采集、故障诊断和课程学习问题。" +
        "每次回答前先在内部执行意图识别与问题优化：判断用户目标、提取设备/协议/错误/约束、补全必要上下文，并把口语化输入转成清晰任务后再作答。" +
        "不要把内部优化过程机械暴露给用户；如果信息不足，先提出最少量澄清问题。回答要清晰、准确、适合学生理解；涉及代码时给出简洁示例；涉及设备操作时提醒安全和权限。"

/**
 * OkHttp implementation of the OpenAI-compatible Chat Completions API.
 */
class OpenAiCompatibleClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : LlmApiService {
    override suspend fun createChatCompletion(
        settings: LlmSettings,
        messages: List<LlmChatMessage>,
        temperature: Double
    ): LlmChatResult = withContext(Dispatchers.IO) {
        validate(settings)

        val bodyJson = JSONObject()
            .put("model", settings.model.trim())
            .put("messages", buildMessagesJson(messages))
            .put("temperature", temperature)
            .put("stream", false)

        val request = Request.Builder()
            .url(chatCompletionsUrl(settings.baseUrl))
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw LlmApiException(parseError(rawBody, response.code))
                }
                parseChatResult(rawBody)
            }
        } catch (error: IOException) {
            throw LlmApiException("API 请求失败：${error.message ?: "网络异常"}", error)
        }
    }

    private fun validate(settings: LlmSettings) {
        when {
            settings.baseUrl.isBlank() -> throw LlmApiException("API Base URL 为空，请先在设置页填写")
            settings.apiKey.isBlank() -> throw LlmApiException("API Key 为空，请先在设置页填写")
            settings.model.isBlank() -> throw LlmApiException("模型名称为空，请先在设置页填写")
        }
    }

    private fun buildMessagesJson(messages: List<LlmChatMessage>): JSONArray {
        val json = JSONArray()
        json.put(
            JSONObject()
                .put("role", "system")
                .put("content", SYSTEM_PROMPT)
        )
        messages.forEach { message ->
            json.put(
                JSONObject()
                    .put("role", message.role)
                    .put("content", message.toContentJson())
            )
        }
        return json
    }

    private fun LlmChatMessage.toContentJson(): Any {
        if (imageDataUrls.isEmpty()) return content

        val contentParts = JSONArray()
        contentParts.put(
            JSONObject()
                .put("type", "text")
                .put("text", content)
        )
        imageDataUrls.forEach { dataUrl ->
            contentParts.put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject().put("url", dataUrl)
                    )
            )
        }
        return contentParts
    }

    private fun parseChatResult(rawBody: String): LlmChatResult {
        val json = JSONObject(rawBody)
        val choices = json.optJSONArray("choices")
            ?: throw LlmApiException("模型返回格式异常：缺少 choices")
        val firstChoice = choices.optJSONObject(0)
            ?: throw LlmApiException("模型返回格式异常：choices 为空")
        val message = firstChoice.optJSONObject("message")
            ?: throw LlmApiException("模型返回格式异常：缺少 message")
        val content = message.optString("content").trim()
        if (content.isBlank()) {
            throw LlmApiException("模型返回内容为空")
        }

        val usage = json.optJSONObject("usage")
        return LlmChatResult(
            content = content,
            promptTokens = usage?.optIntOrNull("prompt_tokens"),
            completionTokens = usage?.optIntOrNull("completion_tokens"),
            totalTokens = usage?.optIntOrNull("total_tokens")
        )
    }

    private fun parseError(rawBody: String, code: Int): String {
        return runCatching {
            val json = JSONObject(rawBody)
            val error = json.optJSONObject("error")
            error?.optString("message")?.takeIf { it.isNotBlank() }
        }.getOrNull()
            ?.let { "API 请求失败($code)：$it" }
            ?: "API 请求失败($code)"
    }

    private fun chatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
