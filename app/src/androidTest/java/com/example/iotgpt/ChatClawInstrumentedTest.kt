package com.example.iotgpt

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.iotgpt.core.preferences.ModelProfile
import com.example.iotgpt.core.preferences.SettingsStore
import com.example.iotgpt.core.testing.AppTestTags
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatClawInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun clawUnknownCommandWritesLocalResult() {
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_CLAW_SWITCH, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(AppTestTags.CHAT_INPUT, useUnmergedTree = true)
            .performTextInput("今天天气怎么样")
        composeRule.onNodeWithTag(AppTestTags.CHAT_SEND, useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "Claw 未识别到本地自动化指令",
                substring = true,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun clawOpenMissingAppWritesFailureInsteadOfBrowserFallback() {
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_CLAW_SWITCH, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(AppTestTags.CHAT_INPUT, useUnmergedTree = true)
            .performTextInput("打开 com.example.missing.app")
        composeRule.onNodeWithTag(AppTestTags.CHAT_SEND, useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "未找到可打开的应用：com.example.missing.app",
                substring = true,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun clawSmsRequiresInAppConfirmationBeforeSending() {
        grantPermission(Manifest.permission.SEND_SMS)
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_CLAW_SWITCH, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(AppTestTags.CHAT_INPUT, useUnmergedTree = true)
            .performTextInput("短信 13800138000 测试安全确认")
        composeRule.onNodeWithTag(AppTestTags.CHAT_SEND, useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "确认发送短信",
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("取消", useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "用户取消二次确认",
                substring = true,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun clawSmsPermissionDeniedWritesLocalResult() {
        revokePermission(Manifest.permission.SEND_SMS)
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_CLAW_SWITCH, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(AppTestTags.CHAT_INPUT, useUnmergedTree = true)
            .performTextInput("短信 13800138000 权限拒绝验证")
        composeRule.onNodeWithTag(AppTestTags.CHAT_SEND, useUnmergedTree = true)
            .performClick()

        denyRuntimePermissionDialog()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "短信权限被拒绝",
                substring = true,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun attachmentMenuShowsMultimodalActions() {
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_ATTACH_MENU, useUnmergedTree = true)
            .performClick()

        listOf("拍照", "文档", "录音").forEach { label ->
            composeRule.onNodeWithText(label, useUnmergedTree = true)
                .assertExistsByPolling()
        }
    }

    @Test
    fun recordingMenuOpensRecordingPanel() {
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_ATTACH_MENU, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("录音", useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasNodeWithTag(AppTestTags.CHAT_RECORDING_PANEL)
        }
    }

    @Test
    fun recordingCanCreatePendingAudioAttachment() {
        grantPermission(Manifest.permission.RECORD_AUDIO)
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_ATTACH_MENU, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("录音", useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithTag(AppTestTags.CHAT_RECORDING_START, useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "REC",
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }

        Thread.sleep(1_200)

        composeRule.onNodeWithTag(AppTestTags.CHAT_RECORDING_STOP_KEEP, useUnmergedTree = true)
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasNodeWithTag(AppTestTags.CHAT_PENDING_ATTACHMENT) &&
                composeRule.onAllNodesWithText(
                    text = "待发送录音",
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recordingAttachmentSendsThroughMockTranscriptionAndChatService() {
        grantPermission(Manifest.permission.RECORD_AUDIO)
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"text":"模拟转写：温度传感器读数异常"}""")
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        """
                        data: {"choices":[{"delta":{"content":"已收到录音转写"},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":6,"total_tokens":18}}

                        data: [DONE]
                        """.trimIndent()
                    )
            )
            configureMockModel(server.url("/").toString())
            completeOnboardingIfNeeded()
            composeRule.onAllNodesWithText("新建", useUnmergedTree = true)
                .onFirst()
                .performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(AppTestTags.CHAT_ATTACH_MENU, useUnmergedTree = true)
                .performClick()
            composeRule.onNodeWithText("录音", useUnmergedTree = true)
                .performClick()
            composeRule.onNodeWithTag(AppTestTags.CHAT_RECORDING_START, useUnmergedTree = true)
                .performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText(
                    text = "REC",
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }
            Thread.sleep(1_200)
            composeRule.onNodeWithTag(AppTestTags.CHAT_RECORDING_STOP_KEEP, useUnmergedTree = true)
                .performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                hasNodeWithTag(AppTestTags.CHAT_PENDING_ATTACHMENT)
            }
            composeRule.onNodeWithTag(AppTestTags.CHAT_SEND, useUnmergedTree = true)
                .performClick()

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText(
                    text = "已收到录音转写",
                    substring = true,
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }

            val transcriptionRequest = server.takeRequest(2, TimeUnit.SECONDS)
            val chatRequest = server.takeRequest(2, TimeUnit.SECONDS)
            assertNotNull(transcriptionRequest)
            assertNotNull(chatRequest)
            assertEquals("/v1/audio/transcriptions", transcriptionRequest?.path)
            assertEquals("/v1/chat/completions", chatRequest?.path)
            assertTrue(chatRequest?.body?.readUtf8().orEmpty().contains("模拟转写"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun documentPickerCancelReturnsToChatWithMessage() {
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_ATTACH_MENU, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("文档", useUnmergedTree = true)
            .performClick()

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wait(
            Until.hasObject(By.pkg("com.google.android.documentsui")),
            3_000
        ) || device.wait(
            Until.hasObject(By.pkg("com.android.documentsui")),
            3_000
        )
        device.pressBack()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text = "未选择文档",
                substring = true,
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun documentPickerCanCreatePendingDocumentAttachment() {
        val fileName = "aiot_picker_probe.txt"
        prepareDownloadTextFile(fileName, "AIoT picker probe from instrumented test")
        completeOnboardingIfNeeded()

        composeRule.onNodeWithTag(AppTestTags.CHAT_ATTACH_MENU, useUnmergedTree = true)
            .performClick()
        composeRule.onNodeWithText("文档", useUnmergedTree = true)
            .performClick()

        selectDocumentInPicker(fileName)

        composeRule.waitUntil(timeoutMillis = 8_000) {
            hasNodeWithTag(AppTestTags.CHAT_PENDING_ATTACHMENT) &&
                composeRule.onAllNodesWithText(
                    text = "待发送文档",
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText(
                    text = fileName,
                    substring = true,
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun completeOnboardingIfNeeded() {
        composeRule.waitUntil(timeoutMillis = 8_000) {
            hasNodeWithTag(AppTestTags.CHAT_INPUT) || hasNodeWithTag(AppTestTags.WELCOME_NEXT)
        }

        repeat(3) {
            if (hasNodeWithTag(AppTestTags.CHAT_INPUT)) return
            composeRule.onNodeWithTag(AppTestTags.WELCOME_NEXT, useUnmergedTree = true)
                .performClick()
            composeRule.waitForIdle()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            hasNodeWithTag(AppTestTags.CHAT_INPUT)
        }
    }

    private fun hasNodeWithTag(tag: String): Boolean {
        return composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }

    private fun grantPermission(permission: String) {
        executeShellCommand("pm grant ${targetPackageName()} $permission")
    }

    private fun configureMockModel(baseUrl: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            SettingsStore(context).upsertModelProfile(
                ModelProfile(
                    id = "instrumented-mock-${System.nanoTime()}",
                    name = "Instrumented Mock",
                    provider = "Mock",
                    baseUrl = baseUrl,
                    apiKey = "test-key",
                    model = "mock-chat",
                    supportsAudioTranscription = true,
                    transcriptionModel = "whisper-1"
                ),
                activate = true
            )
        }
    }

    private fun revokePermission(permission: String) {
        val packageName = targetPackageName()
        executeShellCommand("pm revoke $packageName $permission")
        executeShellCommand("pm clear-permission-flags $packageName $permission user-set user-fixed")
    }

    private fun denyRuntimePermissionDialog() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val denyButton = listOf(
            By.res("com.android.permissioncontroller:id/permission_deny_button"),
            By.res("com.google.android.permissioncontroller:id/permission_deny_button"),
            By.textContains("Don\u2019t allow"),
            By.textContains("Don't allow"),
            By.textContains("Deny"),
            By.textContains("不允许"),
            By.textContains("拒绝")
        ).firstNotNullOfOrNull { selector ->
            device.wait(Until.findObject(selector), 2_000)
        }
        checkNotNull(denyButton) { "Permission deny button not found" }
        denyButton.click()
    }

    private fun prepareDownloadTextFile(fileName: String, content: String) {
        val safeContent = content.replace("'", "")
        executeShellCommand(
            "sh -c \"mkdir -p /sdcard/Download && printf '$safeContent' > /sdcard/Download/$fileName\""
        )
        executeShellCommand(
            "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/$fileName"
        )
    }

    private fun selectDocumentInPicker(fileName: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        waitForDocumentsUi(device)

        val directFile = device.wait(Until.findObject(By.text(fileName)), 3_000)
        if (directFile != null) {
            directFile.click()
            return
        }

        openDownloadsRootIfVisible(device)
        val downloadFile = device.wait(Until.findObject(By.text(fileName)), 5_000)
        checkNotNull(downloadFile) { "Document picker file not found: $fileName" }
        downloadFile.click()
    }

    private fun waitForDocumentsUi(device: UiDevice) {
        val opened = device.wait(
            Until.hasObject(By.pkg("com.google.android.documentsui")),
            5_000
        ) || device.wait(
            Until.hasObject(By.pkg("com.android.documentsui")),
            5_000
        )
        check(opened) { "DocumentsUI did not open" }
    }

    private fun openDownloadsRootIfVisible(device: UiDevice) {
        listOf(
            By.descContains("Show roots"),
            By.descContains("Open navigation drawer"),
            By.descContains("显示根目录"),
            By.descContains("打开导航抽屉")
        ).firstNotNullOfOrNull { selector ->
            device.wait(Until.findObject(selector), 1_000)
        }?.click()

        listOf(
            By.text("Downloads"),
            By.text("Download"),
            By.text("下载"),
            By.text("下载内容")
        ).firstNotNullOfOrNull { selector ->
            device.wait(Until.findObject(selector), 2_000)
        }?.click()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsByPolling() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { fetchSemanticsNode() }.isSuccess
        }
    }

    private fun targetPackageName(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return instrumentation.targetContext.packageName
    }

    private fun executeShellCommand(command: String) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
            .close()
    }
}
