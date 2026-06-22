package com.example.iotgpt.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.core.util.FileUtils
import com.example.iotgpt.feature.chat.LightweightMarkdown
import com.example.iotgpt.feature.chat.MarkdownBlock
import com.example.iotgpt.feature.chat.MarkdownInline
import com.example.iotgpt.ui.theme.LotColors
import com.example.iotgpt.ui.theme.LotRadius
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Empty chat card & prompt deck ───────────────────────────────────────

@Composable
internal fun EmptyChatCard(onExampleSelected: (String) -> Unit) {
    val examples = listOf(
        PromptExample("写作", "帮我润色一段自我介绍", "让表达更自然、清晰、有重点"),
        PromptExample("学习", "把这段知识点讲明白", "用例子解释概念并整理重点"),
        PromptExample("规划", "制定一周复习计划", "按时间、任务和优先级拆分"),
        PromptExample("总结", "把这段资料整理成摘要", "提炼要点、结论和待办事项"),
        PromptExample("生成", "写一封礼貌的邮件", "包含主题、正文和结束语"),
        PromptExample("分析", "帮我比较两个方案", "列出优缺点、风险和建议")
    )
    AppSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Prompt Deck",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "选择一个常用场景，lot 会把它放进输入框继续完善。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        examples.chunked(2).forEach { rowExamples ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowExamples.forEach { example ->
                    PromptDeckItem(
                        example = example,
                        modifier = Modifier.weight(1f),
                        onClick = { onExampleSelected(example.title) }
                    )
                }
                if (rowExamples.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class PromptExample(
    val category: String,
    val title: String,
    val description: String
)

@Composable
private fun PromptDeckItem(
    example: PromptExample,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = when (example.category) {
        "调试" -> MaterialTheme.colorScheme.secondary
        "生成" -> MaterialTheme.colorScheme.tertiary
        "分析" -> LotColors.Warning
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(LotRadius.md))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.28f),
                shape = RoundedCornerShape(LotRadius.md)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = example.category,
            modifier = Modifier
                .clip(RoundedCornerShape(LotRadius.sm))
                .background(tint.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = example.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = example.description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Message bubble ──────────────────────────────────────────────────────

@Composable
internal fun MessageBubble(
    message: MessageEntity,
    showHeader: Boolean,
    modelLabel: String,
    reduceMotion: Boolean,
    onCopy: (String) -> Unit,
    onRetry: (String) -> Unit
) {
    val isUser = message.role == "user"
    val isClaw = message.role == "claw"
    val canRetry = !isUser &&
        !isClaw &&
        !message.isStreaming &&
        isRetryableAssistantError(message.content)
    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isClaw -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.74f)
        canRetry -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val outlineColor = when {
        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isClaw -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
        canRetry -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    }
    val labelText = when {
        isUser -> "用户"
        isClaw -> "Claw 本地"
        message.isStreaming -> "AI 助手 · 思考中"
        canRetry -> "AI 助手 · 失败"
        else -> "AI 助手"
    }
    val labelColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        isClaw -> MaterialTheme.colorScheme.onSecondaryContainer
        canRetry -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.80f else 0.86f)
                .widthIn(max = 560.dp)
                .background(
                    color = bubbleColor,
                    shape = RoundedCornerShape(LotRadius.md)
                )
                .border(1.dp, outlineColor, RoundedCornerShape(LotRadius.md))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(min = 36.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val displayHeader = showHeader || message.isStreaming || canRetry || isClaw
            if (displayHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = labelText,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor.copy(alpha = 0.64f)
                    )
                }
            }
            FileUtils.parseAttachmentJson(message.attachmentJson)?.let { attachment ->
                AttachmentCard(attachment)
            }
            if (message.isStreaming) {
                if (message.content.isBlank()) {
                    ThinkingIndicator(modelLabel = modelLabel, reduceMotion = reduceMotion)
                } else {
                    MessageContent(message.content, onCopy)
                    ThinkingIndicator(modelLabel = modelLabel, reduceMotion = reduceMotion)
                }
            } else {
                MessageContent(message.content, onCopy)
            }
            if (!message.isStreaming && message.content.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onCopy(message.content) }) {
                        Text("复制")
                    }
                    if (canRetry) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { onRetry(message.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

internal fun isRetryableAssistantError(content: String): Boolean {
    return content.startsWith("API") ||
        content.startsWith("模型") ||
        content.contains("请求失败")
}

// ── Message content & code block ────────────────────────────────────────

@Composable
private fun MessageContent(content: String, onCopy: (String) -> Unit) {
    val blocks = remember(content) { LightweightMarkdown.parse(content) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> MarkdownText(block.inlines)
                is MarkdownBlock.BulletList -> MarkdownBulletList(block.items)
                is MarkdownBlock.NumberedList -> MarkdownNumberedList(block.items)
                is MarkdownBlock.Code -> CodeBlock(
                    rawCode = buildString {
                        if (block.language != "代码") {
                            appendLine(block.language)
                        }
                        append(block.code)
                    },
                    onCopy = onCopy
                )
            }
        }
    }
}

@Composable
private fun MarkdownText(inlines: List<MarkdownInline>, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            inlines.forEach { inline ->
                when (inline) {
                    is MarkdownInline.Text -> append(inline.value)
                    is MarkdownInline.Bold -> pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)).also {
                        append(inline.value)
                        pop()
                    }
                }
            }
        },
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun MarkdownBulletList(items: List<List<MarkdownInline>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MarkdownText(item, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MarkdownNumberedList(items: List<MarkdownBlock.NumberedItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${item.number}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MarkdownText(item.inlines, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CodeBlock(rawCode: String, onCopy: (String) -> Unit) {
    val lines = rawCode.trim().lines()
    val firstLine = lines.firstOrNull().orEmpty()
    val hasLanguage = firstLine.matches(Regex("[A-Za-z0-9_+.#-]{1,24}")) && lines.size > 1
    val language = if (hasLanguage) firstLine else "代码"
    val code = if (hasLanguage) {
        lines.drop(1).joinToString("\n")
    } else {
        rawCode.trim()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(LotRadius.md)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = { onCopy(code) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("复制", style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            text = code,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false
        )
    }
}

// ── Shared utility ──────────────────────────────────────────────────────

internal fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
