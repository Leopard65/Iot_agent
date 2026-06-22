package com.example.iotgpt.feature.chat

import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.core.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ConversationMarkdownExporter {
    fun export(
        conversation: ConversationEntity,
        messages: List<MessageEntity>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("# ${conversation.title.ifBlank { "lot 对话" }}")
            appendLine()
            appendLine("- 导出时间：${dateFormat.format(Date(System.currentTimeMillis()))}")
            appendLine("- 模型：${conversation.modelId.ifBlank { "未知" }}")
            appendLine("- 消息数：${messages.size}")
            conversation.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                appendLine("- 摘要：${summary.replace("\n", " ")}")
            }
            appendLine()
            appendLine("---")
            appendLine()

            messages.sortedBy { it.createdAt }.forEach { message ->
                val label = roleLabel(message.role)
                appendLine("## $label · ${dateFormat.format(Date(message.createdAt))}")
                appendLine()
                FileUtils.parseAttachmentJson(message.attachmentJson)?.let { attachment ->
                    appendLine("> 附件：${attachment.displayName} · ${attachment.mimeType ?: "未知类型"}")
                    appendLine()
                }
                appendLine(message.content.ifBlank { "（空消息）" })
                appendLine()
            }
        }.trimEnd() + "\n"
    }

    fun fileName(conversation: ConversationEntity): String {
        val safeTitle = conversation.title
            .ifBlank { "lot-conversation" }
            .replace(Regex("""[\\/:*?"<>|]+"""), "_")
            .take(36)
            .trim()
            .ifBlank { "lot-conversation" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${safeTitle}_$stamp.md"
    }

    private fun roleLabel(role: String): String {
        return when (role) {
            "user" -> "用户"
            "assistant" -> "AI 助手"
            "claw" -> "Claw 本地"
            else -> role.ifBlank { "消息" }
        }
    }
}
