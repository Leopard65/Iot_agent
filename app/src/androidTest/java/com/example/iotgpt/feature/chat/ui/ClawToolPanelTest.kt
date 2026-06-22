package com.example.iotgpt.feature.chat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.iotgpt.core.testing.AppTestTags
import com.example.iotgpt.ui.theme.LotTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Compose UI tests for [ClawToolPanel]. Tests the panel directly without
 * requiring the full ChatScreen or ViewModel.
 */
@RunWith(AndroidJUnit4::class)
class ClawToolPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var lastMode: ClawPanelMode? = null
    private var lastAction: String? = null

    private fun setPanel(activeMode: ClawPanelMode? = null) {
        lastMode = activeMode
        lastAction = null
        composeRule.setContent {
            // activeModeState is Compose state so that clicks (which call
            // onModeSelected) trigger recomposition and show inline inputs.
            var activeModeState by remember { mutableStateOf(activeMode) }
            LotTheme(darkTheme = false, dynamicColor = false) {
                ClawToolPanel(
                    activeMode = activeModeState,
                    downloadInput = "",
                    smsPhoneInput = "",
                    smsContentInput = "",
                    appLeapInput = "",
                    onDownloadInputChange = {},
                    onSmsPhoneChange = {},
                    onSmsContentChange = {},
                    onAppLeapInputChange = {},
                    onModeSelected = {
                        activeModeState = it
                        lastMode = it
                    },
                    onPhoto = { lastAction = "photo" },
                    onDocument = { lastAction = "document" },
                    onAudio = { lastAction = "audio" },
                    onDownloadSubmit = { lastAction = "download" },
                    onSmsSubmit = { lastAction = "sms" },
                    onAppLeapSubmit = { lastAction = "appLeap" }
                )
            }
        }
    }

    @Test
    fun panelRendersAllSixToolButtons() {
        setPanel()

        composeRule.onNodeWithTag(AppTestTags.CLAW_TOOL_PANEL).assertIsDisplayed()
        composeRule.onNodeWithText("拍照").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("文档").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("录音").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("下载").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("短信").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("App-Leap").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun testTagsArePresent() {
        setPanel()

        composeRule.onNodeWithTag(AppTestTags.CLAW_TOOL_PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.CLAW_TOOL_DOWNLOAD).assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.CLAW_TOOL_SMS).assertIsDisplayed()
        composeRule.onNodeWithTag(AppTestTags.CLAW_TOOL_APP_LEAP).assertIsDisplayed()
    }

    @Test
    fun clickingDownloadShowsInlineInput() {
        setPanel()

        composeRule.onNodeWithTag(AppTestTags.CLAW_TOOL_DOWNLOAD).performClick()
        // After click, the download inline input should appear with placeholder text
        composeRule.onNodeWithText("https://example.com/file.zip").assertIsDisplayed()
        composeRule.onAllNodesWithText("下载").assertCountEquals(2)
    }

    @Test
    fun clickingSmsShowsPhoneAndContentInputs() {
        setPanel()

        composeRule.onNodeWithTag(AppTestTags.CLAW_TOOL_SMS).performClick()
        composeRule.onNodeWithText("手机号").assertIsDisplayed()
        composeRule.onNodeWithText("短信内容").assertIsDisplayed()
        composeRule.onNodeWithText("发送").assertIsDisplayed()
    }

    @Test
    fun clickingAppLeapShowsInlineInput() {
        setPanel()

        composeRule.onNodeWithTag(AppTestTags.CLAW_TOOL_APP_LEAP).performClick()
        composeRule.onNodeWithText("打开 Wi-Fi / 搜索天气 / https://...").assertIsDisplayed()
        composeRule.onNodeWithText("跃迁").assertIsDisplayed()
    }

    @Test
    fun nullActiveModeShowsNoInlineInput() {
        setPanel(activeMode = null)

        // When no mode is active, none of the inline input placeholders should appear
        composeRule.onNodeWithText("https://example.com/file.zip", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("手机号", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("打开 Wi-Fi / 搜索天气 / https://...", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun downloadModeShowsDownloadInput() {
        setPanel(activeMode = ClawPanelMode.Download)

        composeRule.onNodeWithText("https://example.com/file.zip").assertIsDisplayed()
        // SMS and AppLeap inputs should NOT be visible
        composeRule.onNodeWithText("手机号", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("打开 Wi-Fi / 搜索天气 / https://...", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun smsModeShowsSmsInputs() {
        setPanel(activeMode = ClawPanelMode.Sms)

        composeRule.onNodeWithText("手机号").assertIsDisplayed()
        composeRule.onNodeWithText("短信内容").assertIsDisplayed()
        // Download and AppLeap should NOT be visible
        composeRule.onNodeWithText("https://example.com/file.zip", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("打开 Wi-Fi / 搜索天气 / https://...", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun appLeapModeShowsAppLeapInput() {
        setPanel(activeMode = ClawPanelMode.AppLeap)

        composeRule.onNodeWithText("打开 Wi-Fi / 搜索天气 / https://...").assertIsDisplayed()
        // Download and SMS should NOT be visible
        composeRule.onNodeWithText("https://example.com/file.zip", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("手机号", useUnmergedTree = true).assertDoesNotExist()
    }
}
