package com.example.iotgpt.core.network

import com.example.iotgpt.core.preferences.LlmSettings
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiCompatibleClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenAiCompatibleClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sendsTextOnlyMessagesAsStringContentAndParsesUsage() = runBlocking {
        server.enqueue(chatResponse())

        val result = client.createChatCompletion(
            settings = settings(baseUrl = server.url("/compatible-mode").toString()),
            messages = listOf(
                LlmChatMessage(role = "user", content = "Explain MQTT QoS 1")
            ),
            temperature = 0.25
        )

        assertEquals("ok", result.content)
        assertEquals(11, result.promptTokens)
        assertEquals(3, result.completionTokens)
        assertEquals(14, result.totalTokens)

        val request = server.takeRequest()
        assertEquals("/compatible-mode/v1/chat/completions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))

        val body = JSONObject(request.body.readUtf8())
        assertEquals("test-model", body.getString("model"))
        assertEquals(0.25, body.getDouble("temperature"), 0.0)
        assertEquals(false, body.getBoolean("stream"))

        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        val user = messages.getJSONObject(1)
        assertEquals("user", user.getString("role"))
        assertEquals("Explain MQTT QoS 1", user.getString("content"))
    }

    @Test
    fun sendsVisionMessagesAsOpenAiCompatibleContentParts() = runBlocking {
        server.enqueue(chatResponse())

        client.createChatCompletion(
            settings = settings(baseUrl = server.url("/v1").toString()),
            messages = listOf(
                LlmChatMessage(
                    role = "user",
                    content = "Read this meter",
                    imageDataUrls = listOf(
                        "data:image/jpeg;base64,AAA",
                        "data:image/png;base64,BBB"
                    )
                )
            )
        )

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)

        val body = JSONObject(request.body.readUtf8())
        val user = body.getJSONArray("messages").getJSONObject(1)
        val content = user.getJSONArray("content")
        assertEquals(3, content.length())

        val text = content.getJSONObject(0)
        assertEquals("text", text.getString("type"))
        assertEquals("Read this meter", text.getString("text"))

        val firstImage = content.getJSONObject(1)
        assertEquals("image_url", firstImage.getString("type"))
        assertEquals(
            "data:image/jpeg;base64,AAA",
            firstImage.getJSONObject("image_url").getString("url")
        )

        val secondImage = content.getJSONObject(2)
        assertEquals("image_url", secondImage.getString("type"))
        assertEquals(
            "data:image/png;base64,BBB",
            secondImage.getJSONObject("image_url").getString("url")
        )
    }

    @Test
    fun acceptsBaseUrlAlreadyPointingToChatCompletions() = runBlocking {
        server.enqueue(chatResponse())

        client.createChatCompletion(
            settings = settings(baseUrl = server.url("/v1/chat/completions").toString()),
            messages = listOf(LlmChatMessage(role = "user", content = "hello"))
        )

        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun exposesReadableApiErrorMessage() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "error": {
                        "message": "bad key"
                      }
                    }
                    """.trimIndent()
                )
        )

        val error = runCatching {
            client.createChatCompletion(
                settings = settings(baseUrl = server.url("/").toString()),
                messages = listOf(LlmChatMessage(role = "user", content = "hello"))
            )
        }.exceptionOrNull()

        assertTrue(error is LlmApiException)
        assertEquals("API 请求失败(401)：bad key", error?.message)
    }

    private fun settings(baseUrl: String): LlmSettings {
        return LlmSettings(
            baseUrl = baseUrl,
            apiKey = "test-key",
            model = "test-model",
            supportsVision = true
        )
    }

    private fun chatResponse(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "ok"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 11,
                    "completion_tokens": 3,
                    "total_tokens": 14
                  }
                }
                """.trimIndent()
            )
    }
}
