package com.example.iotgpt.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.iotgpt.core.database.AppDatabase
import com.example.iotgpt.core.network.LlmApiService
import com.example.iotgpt.core.network.LlmAudioInput
import com.example.iotgpt.core.network.LlmChatMessage
import com.example.iotgpt.core.network.LlmChatResult
import com.example.iotgpt.core.preferences.LlmSettings
import com.example.iotgpt.core.preferences.ModelProfile
import com.example.iotgpt.core.preferences.SettingsStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatRepositoryAttachmentInstrumentedTest {
    @Test
    fun documentAttachmentTextPreviewIsSentToLlmContext() = runBlocking {
        val harness = newHarness(supportsVision = false)
        try {
            harness.repository.sendAttachmentMessage(
                conversationId = null,
                content = "用户上传文档：aiot_notes.txt，大小 1.0 KB，类型 text/plain。",
                attachmentJson = attachmentJson(
                    type = "document",
                    uri = "content://example/document",
                    displayName = "aiot_notes.txt",
                    mimeType = "text/plain",
                    textPreview = "MQTT QoS 1 表示至少送达一次。"
                )
            )

            val userMessage = harness.fake.lastUserMessage()
            assertTrue(userMessage.content.contains("附件可解析正文"))
            assertTrue(userMessage.content.contains("MQTT QoS 1 表示至少送达一次。"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun audioAttachmentAddsTranscriptionLimitationHint() = runBlocking {
        val harness = newHarness(supportsVision = false)
        try {
            harness.repository.sendAttachmentMessage(
                conversationId = null,
                content = "用户上传录音：recording.wav，大小 12.0 KB，类型 audio/wav。",
                attachmentJson = attachmentJson(
                    type = "audio",
                    uri = "content://example/audio",
                    displayName = "recording.wav",
                    mimeType = "audio/wav"
                )
            )

            val userMessage = harness.fake.lastUserMessage()
            assertTrue(userMessage.content.contains("包含录音附件"))
            assertTrue(userMessage.content.contains("不支持直接转写音频"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun audioAttachmentWithTranscriptionAddsTranscriptToContext() = runBlocking {
        val harness = newHarness(
            supportsVision = false,
            supportsAudioTranscription = true
        )
        try {
            val audioUri = createTinyAudio(harness.context)
            harness.fake.transcriptionResult = "实验录音：温度传感器读数异常。"

            harness.repository.sendAttachmentMessage(
                conversationId = null,
                content = "用户上传录音：recording.wav，大小 12.0 KB，类型 audio/wav。",
                attachmentJson = attachmentJson(
                    type = "audio",
                    uri = audioUri.toString(),
                    displayName = "recording.wav",
                    mimeType = "audio/wav"
                )
            )

            val userMessage = harness.fake.lastUserMessage()
            assertEquals(1, harness.fake.audioCalls.size)
            assertTrue(userMessage.content.contains("录音转写文本"))
            assertTrue(userMessage.content.contains("温度传感器读数异常"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun audioTranscriptionIsCachedForRegeneration() = runBlocking {
        val harness = newHarness(
            supportsVision = false,
            supportsAudioTranscription = true
        )
        try {
            val audioUri = createTinyAudio(harness.context)
            harness.fake.transcriptionResult = "缓存转写：湿度超过阈值。"

            val conversationId = harness.repository.sendAttachmentMessage(
                conversationId = null,
                content = "用户上传录音：recording.wav，大小 12.0 KB，类型 audio/wav。",
                attachmentJson = attachmentJson(
                    type = "audio",
                    uri = audioUri.toString(),
                    displayName = "recording.wav",
                    mimeType = "audio/wav"
                )
            )

            harness.repository.regenerateLastAssistant(conversationId)

            assertEquals(1, harness.fake.audioCalls.size)
            val userMessage = harness.fake.lastUserMessage()
            assertTrue(userMessage.content.contains("缓存转写"))
        } finally {
            harness.close()
        }
    }

    @Test
    fun imageAttachmentWithoutVisionAddsTextFallbackHint() = runBlocking {
        val harness = newHarness(supportsVision = false)
        try {
            val imageUri = createTinyJpeg(harness.context)
            harness.repository.sendAttachmentMessage(
                conversationId = null,
                content = "用户上传图片：vision.jpg，大小 1.0 KB，类型 image/jpeg。",
                attachmentJson = attachmentJson(
                    type = "image",
                    uri = imageUri.toString(),
                    displayName = "vision.jpg",
                    mimeType = "image/jpeg"
                )
            )

            val userMessage = harness.fake.lastUserMessage()
            assertTrue(userMessage.content.contains("当前模型配置未开启图片输入"))
            assertTrue(userMessage.imageDataUrls.isEmpty())
        } finally {
            harness.close()
        }
    }

    @Test
    fun imageAttachmentWithVisionSendsImageDataUrl() = runBlocking {
        val harness = newHarness(supportsVision = true)
        try {
            val imageUri = createTinyJpeg(harness.context)
            harness.repository.sendAttachmentMessage(
                conversationId = null,
                content = "用户上传图片：vision.jpg，大小 1.0 KB，类型 image/jpeg。",
                attachmentJson = attachmentJson(
                    type = "image",
                    uri = imageUri.toString(),
                    displayName = "vision.jpg",
                    mimeType = "image/jpeg"
                )
            )

            val userMessage = harness.fake.lastUserMessage()
            assertEquals(1, userMessage.imageDataUrls.size)
            assertTrue(userMessage.imageDataUrls.first().startsWith("data:image/jpeg;base64,"))
        } finally {
            harness.close()
        }
    }

    private suspend fun newHarness(
        supportsVision: Boolean,
        supportsAudioTranscription: Boolean = false
    ): RepositoryHarness {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settingsStore = SettingsStore.createForTest(context, createTestDataStoreFile(context))
        val profileId = "test-profile-${System.nanoTime()}"
        settingsStore.upsertModelProfile(
            ModelProfile(
                id = profileId,
                name = "Test Model",
                provider = "Test",
                baseUrl = "https://example.invalid",
                apiKey = "test-key",
                model = "test-model",
                supportsVision = supportsVision,
                supportsAudioTranscription = supportsAudioTranscription,
                transcriptionModel = "whisper-1"
            ),
            activate = true
        )
        val fake = RecordingLlmApiService()
        return RepositoryHarness(
            context = context,
            database = database,
            fake = fake,
            repository = ChatRepositoryImpl(
                database = database,
                settingsStore = settingsStore,
                appContext = context,
                llmApiService = fake,
                notificationHelper = null
            )
        )
    }

    private fun createTestDataStoreFile(context: Context): File {
        val dir = File(context.cacheDir, "settings-test").apply { mkdirs() }
        return File(dir, "iotgpt_${System.nanoTime()}.preferences_pb")
    }

    private fun attachmentJson(
        type: String,
        uri: String,
        displayName: String,
        mimeType: String,
        textPreview: String? = null
    ): String {
        return JSONObject()
            .put("type", type)
            .put("uri", uri)
            .put("displayName", displayName)
            .put("sizeBytes", 1024L)
            .put("mimeType", mimeType)
            .put("textPreview", textPreview ?: JSONObject.NULL)
            .toString()
    }

    private fun createTinyJpeg(context: Context): Uri {
        val file = File(context.cacheDir, "vision_${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun createTinyAudio(context: Context): Uri {
        val file = File(context.cacheDir, "recording_${System.nanoTime()}.wav")
        file.writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        return Uri.fromFile(file)
    }

    private data class RepositoryHarness(
        val context: Context,
        val database: AppDatabase,
        val fake: RecordingLlmApiService,
        val repository: ChatRepositoryImpl
    ) {
        fun close() {
            database.close()
        }
    }

    private class RecordingLlmApiService : LlmApiService {
        val calls = mutableListOf<List<LlmChatMessage>>()
        val audioCalls = mutableListOf<LlmAudioInput>()
        var transcriptionResult = "转写文本"

        override suspend fun createChatCompletion(
            settings: LlmSettings,
            messages: List<LlmChatMessage>,
            temperature: Double
        ): LlmChatResult {
            calls += messages
            return LlmChatResult(
                content = "测试回复",
                promptTokens = 8,
                completionTokens = 4,
                totalTokens = 12
            )
        }

        override suspend fun transcribeAudio(
            settings: LlmSettings,
            audio: LlmAudioInput
        ): String {
            audioCalls += audio
            return transcriptionResult
        }

        fun lastUserMessage(): LlmChatMessage {
            return calls.last().last { it.role == "user" }
        }
    }
}
