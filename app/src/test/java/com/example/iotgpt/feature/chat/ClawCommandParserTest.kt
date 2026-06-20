package com.example.iotgpt.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ClawCommandParserTest {
    @Test
    fun parsesCameraCommand() {
        assertEquals(ClawCommand.Camera, parseClawCommand("帮我拍照"))
        assertEquals(ClawCommand.Camera, parseClawCommand("open camera"))
    }

    @Test
    fun parsesDocumentCommand() {
        assertEquals(ClawCommand.Document, parseClawCommand("选择文档上传"))
        assertEquals(ClawCommand.Document, parseClawCommand("pick a file"))
    }

    @Test
    fun parsesAudioCommand() {
        assertEquals(ClawCommand.Audio, parseClawCommand("开始录音"))
        assertEquals(ClawCommand.Audio, parseClawCommand("record voice"))
    }

    @Test
    fun parsesDownloadCommandWithUrl() {
        assertEquals(
            ClawCommand.Download("https://example.com/file.bin"),
            parseClawCommand("下载 https://example.com/file.bin")
        )
    }

    @Test
    fun parsesDownloadCommandWithoutUrl() {
        assertEquals(ClawCommand.Download(null), parseClawCommand("下载文件"))
    }

    @Test
    fun parsesSmsCommandAndNormalizesPhoneNumber() {
        assertEquals(
            ClawCommand.Sms(
                phoneNumber = "13800138000",
                content = "测试短信内容"
            ),
            parseClawCommand("短信 138 0013-8000 测试短信内容")
        )
    }

    @Test
    fun parsesSmsCommandWithDefaultContent() {
        assertEquals(
            ClawCommand.Sms(
                phoneNumber = "13800138000",
                content = "lot 短信确认"
            ),
            parseClawCommand("sms 13800138000")
        )
    }

    @Test
    fun parsesUrlAsAppLeapWhenNoDownloadKeyword() {
        assertEquals(
            ClawCommand.AppLeap("https://example.com"),
            parseClawCommand("https://example.com")
        )
    }

    @Test
    fun parsesSystemIntentCommand() {
        assertEquals(ClawCommand.AppLeap("打开 Wi-Fi"), parseClawCommand("打开 Wi-Fi"))
        assertEquals(ClawCommand.AppLeap("打开qq"), parseClawCommand("打开qq"))
        assertEquals(ClawCommand.AppLeap("搜索 MQTT 调试"), parseClawCommand("搜索 MQTT 调试"))
    }

    @Test
    fun parsesPhoneNavigationAndInstallAsAppLeap() {
        assertEquals(ClawCommand.AppLeap("拨打 13800138000"), parseClawCommand("拨打 13800138000"))
        assertEquals(ClawCommand.AppLeap("导航到 北京邮电大学"), parseClawCommand("导航到 北京邮电大学"))
        assertEquals(ClawCommand.AppLeap("安装 微信"), parseClawCommand("安装 微信"))
    }

    @Test
    fun parsesUnknownCommand() {
        assertEquals(ClawCommand.Unknown, parseClawCommand("今天天气怎么样"))
    }
}
