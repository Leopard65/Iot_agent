package com.example.iotgpt.core.network

import com.example.iotgpt.core.preferences.LlmSettings
import java.io.IOException
import java.util.Base64
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
    "你是 lot，一个面向普通用户的通用 AI 智能助手，可以帮助用户完成问答解释、写作润色、学习规划、代码辅助、资料整理、生活建议和问题排查。" +
        "每次回答前先在内部执行意图识别与问题优化：判断用户目标、提取关键背景、补全必要约束，并把口语化输入转成清晰任务后再作答。" +
        "不要把内部优化过程机械暴露给用户；如果信息不足，先提出最少量澄清问题。回答要清晰、准确、自然，优先给出可执行步骤；涉及代码时给出简洁示例；涉及系统权限、隐私或现实操作时提醒风险和确认。"

/**
 * OkHttp implementation of the OpenAI-compatible Chat Completions API.
 */
class OpenAiCompatibleClient(
    private val client: OkHttpClient = sharedClient
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

        val request = if (settings.usesMimoAsr()) {
            buildMimoAudioTranscriptionRequest(settings, audio)
        } else {
            buildAudioTranscriptionRequest(settings, audio)
        }
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
            .header("Content-Type", "application/json")
            .addAuthHeaders(settings)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildMimoAudioTranscriptionRequest(
        settings: LlmSettings,
        audio: LlmAudioInput
    ): Request {
        val mimeType = audio.mimeTypeForMimo()
        val dataUrl = "data:$mimeType;base64,${Base64.getEncoder().encodeToString(audio.bytes)}"
        val content = JSONArray().put(
            JSONObject()
                .put("type", "input_audio")
                .put(
                    "input_audio",
                    JSONObject().put("data", dataUrl)
                )
        )
        val messages = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", content)
        )
        val bodyJson = JSONObject()
            .put("model", settings.transcriptionModel.ifBlank { "mimo-v2.5-asr" })
            .put("messages", messages)
            .put(
                "asr_options",
                JSONObject().put("language", "auto")
            )

        return Request.Builder()
            .url(chatCompletionsUrl(settings.baseUrl))
            .header("Content-Type", "application/json")
            .addAuthHeaders(settings)
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
            .addAuthHeaders(settings)
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
        val json = JSONObject(rawBody)
        val text = json.optString("text").trim()
        if (text.isNotBlank()) return text
        return parseChatResult(rawBody).content
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

    private fun Request.Builder.addAuthHeaders(settings: LlmSettings): Request.Builder {
        val key = settings.apiKey.trim()
        return if (settings.usesMimoAuth()) {
            header("api-key", key)
        } else {
            header("Authorization", "Bearer $key")
        }
    }

    private fun LlmSettings.usesMimoAsr(): Boolean {
        return isMimoBaseUrl() || transcriptionModel.contains("mimo", ignoreCase = true)
    }

    private fun LlmSettings.isMimoBaseUrl(): Boolean {
        return baseUrl.contains("xiaomimimo", ignoreCase = true)
    }

    private fun LlmSettings.usesMimoAuth(): Boolean {
        return isMimoBaseUrl() ||
            model.contains("mimo", ignoreCase = true) ||
            transcriptionModel.contains("mimo", ignoreCase = true)
    }

    private fun LlmAudioInput.mimeTypeForMimo(): String {
        val normalized = mimeType.orEmpty().lowercase()
        val lowerName = fileName.lowercase()
        return when {
            normalized in setOf("audio/wav", "audio/x-wav", "audio/wave") ||
                lowerName.endsWith(".wav") -> "audio/wav"
            normalized in setOf("audio/mpeg", "audio/mp3") ||
                lowerName.endsWith(".mp3") -> "audio/mpeg"
            else -> throw LlmApiException(
                "MiMo ASR 仅支持 WAV 或 MP3 音频，请使用 App 内录音生成 WAV，或上传 WAV/MP3 文件。"
            )
        }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()

        // 共享单个 OkHttpClient（持有连接池与线程池），避免每个实例各建一份。
        val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
