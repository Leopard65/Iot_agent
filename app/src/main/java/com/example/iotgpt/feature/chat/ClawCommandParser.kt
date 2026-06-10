package com.example.iotgpt.feature.chat

sealed interface ClawCommand {
    data object Camera : ClawCommand
    data object Document : ClawCommand
    data object Audio : ClawCommand
    data class Download(val url: String?) : ClawCommand
    data class Sms(val phoneNumber: String?, val content: String) : ClawCommand
    data class AppLeap(val command: String) : ClawCommand
    data object Unknown : ClawCommand
}

fun parseClawCommand(text: String): ClawCommand {
    val normalized = text.trim()
    val lower = normalized.lowercase()
    val url = Regex("""https?://\S+""").find(normalized)?.value
    val phoneNumberMatch = Regex("""\+?\d[\d\s-]{5,}\d""").find(normalized)
    val phoneNumber = phoneNumberMatch
        ?.value
        ?.replace(Regex("""[\s-]"""), "")

    return when {
        listOf("拍照", "相机", "camera").any { lower.contains(it) } -> ClawCommand.Camera
        listOf("下载", "download").any { lower.contains(it) } -> ClawCommand.Download(url)
        listOf("文档", "文件", "选择文件", "选择文档", "上传文件", "上传文档", "document", "file")
            .any { lower.contains(it) } -> ClawCommand.Document
        listOf("录音", "音频", "语音", "麦克风", "record", "audio", "voice")
            .any { lower.contains(it) } -> ClawCommand.Audio
        listOf("短信", "sms").any { lower.contains(it) } -> {
            val content = normalized
                .replaceFirst("短信", "")
                .replaceFirst("SMS", "", ignoreCase = true)
                .replace(phoneNumberMatch?.value.orEmpty(), "")
                .trim()
                .ifBlank { "AIoT Assistant 短信演示" }
            ClawCommand.Sms(phoneNumber, content)
        }
        url != null -> ClawCommand.AppLeap(url)
        listOf("搜索", "search", "浏览器", "browser", "打开", "open", "应用", "app", "wifi", "wi-fi", "蓝牙", "设置")
            .any { lower.contains(it) } -> ClawCommand.AppLeap(normalized)
        else -> ClawCommand.Unknown
    }
}
