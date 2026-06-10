package com.example.iotgpt.core.network

import com.example.iotgpt.core.preferences.LlmSettings
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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

        val request = buildChatRequest(
            settings = settings,
            messages = messages,
            temperature = temperature,
            stream = false
        )

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

    override suspend fun transcribeAudio(
        settings: LlmSettings,
        audio: LlmAudioInput
    ): String = withContext(Dispatchers.IO) {
        validate(settings)
        if (audio.bytes.isEmpty()) {
            throw LlmApiException("录音文件为空，无法转写")
        }

        val request = buildAudioTranscriptionRequest(settings, audio)
        try {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw LlmApiException(parseError(rawBody, response.code))
                }
                parseTranscriptionResult(rawBody)
            }
        } catch (error: IOException) {
            throw LlmApiException("语音转写请求失败：${error.message ?: "网络异常"}", error)
        }
    }

    override fun streamChatCompletion(
        settings: LlmSettings,
        messages: List<LlmChatMessage>,
        temperature: Double
    ): Flow<LlmChatStreamChunk> = flow {
        validate(settings)

        val request = buildChatRequest(
            settings = settings,
            messages = messages,
            temperature = temperature,
            stream = true
        )

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val rawBody = response.body?.string().orEmpty()
                    throw LlmApiException(parseError(rawBody, response.code))
                }
                val body = response.body ?: throw LlmApiException("模型返回内容为空")
                body.charStream().buffered().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        currentCoroutineContext().ensureActive()
                        parseSseLine(line)?.let { chunk ->
                            emit(chunk)
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (error: IOException) {
            throw LlmApiException("API 请求失败：${error.message ?: "网络异常"}", error)
        }
    }.flowOn(Dispatchers.IO)

    private fun validate(settings: LlmSettings) {
        when {
            settings.baseUrl.isBlank() -> throw LlmApiException("API Base URL 为空，请先在设置页填写")
            settings.apiKey.isBlank() -> throw LlmApiException("API Key 为空，请先在设置页填写")
            settings.model.isBlank() -> throw LlmApiException("模型名称为空，请先在设置页填写")
        }
    }

    private fun buildChatRequest(
        settings: LlmSettings,
        messages: List<LlmChatMessage>,
        temperature: Double,
        stream: Boolean
    ): Request {
        val bodyJson = JSONObject()
            .put("model", settings.model.trim())
            .put("messages", buildMessagesJson(messages))
            .put("temperature", temperature)
            .put("stream", stream)
        if (settings.reasoningEnabled) {
            bodyJson
                .put("enable_thinking", true)
                .put("thinking", JSONObject().put("type", "enabled"))
        }

        return Request.Builder()
            .url(chatCompletionsUrl(settings.baseUrl))
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildAudioTranscriptionRequest(
        settings: LlmSettings,
        audio: LlmAudioInput
    ): Request {
        val audioMediaType = audio.mimeType
            ?.takeIf { it.isNotBlank() }
            ?.toMediaTypeOrNull()
            ?: OCTET_STREAM_MEDIA_TYPE
        val fileName = audio.fileName.ifBlank { "recording.m4a" }
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "model",
                settings.transcriptionModel.ifBlank { "whisper-1" }
            )
            .addFormDataPart(
                "file",
                fileName,
                audio.bytes.toRequestBody(audioMediaType)
            )
            .build()

        return Request.Builder()
            .url(audioTranscriptionsUrl(settings.baseUrl))
            .header("Authorization", "Bearer ${settings.apiKey.trim()}")
            .post(body)
            .build()
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

    private fun parseTranscriptionResult(rawBody: String): String {
        val text = JSONObject(rawBody).optString("text").trim()
        if (text.isBlank()) {
            throw LlmApiException("语音转写返回内容为空")
        }
        return text
    }

    private fun parseSseLine(line: String): LlmChatStreamChunk? {
        val trimmed = line.trim()
        if (trimmed.startsWith(":")) return null
        if (!trimmed.startsWith("data:")) return null

        val data = trimmed.removePrefix("data:").trim()
        if (data.isBlank()) return null
        if (data == "[DONE]") {
            return LlmChatStreamChunk(finishReason = "stop")
        }

        val json = JSONObject(data)
        val usage = json.optJSONObject("usage")
        val choices = json.optJSONArray("choices")
        val firstChoice = choices?.optJSONObject(0)
        val delta = firstChoice?.optJSONObject("delta")
        val content = delta
            ?.takeIf { it.has("content") && !it.isNull("content") }
            ?.optString("content")
            .orEmpty()
        return LlmChatStreamChunk(
            delta = content,
            finishReason = firstChoice?.optString("finish_reason")?.takeIf { it.isNotBlank() && it != "null" },
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

    private fun audioTranscriptionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/audio/transcriptions") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/audio/transcriptions"
            else -> "$trimmed/v1/audio/transcriptions"
        }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}
