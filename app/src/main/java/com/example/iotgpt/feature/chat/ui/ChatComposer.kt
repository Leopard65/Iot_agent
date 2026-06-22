package com.example.iotgpt.feature.chat.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone
import com.example.iotgpt.core.testing.AppTestTags
import com.example.iotgpt.ui.theme.LotMotion
import com.example.iotgpt.ui.theme.LotRadius
import com.example.iotgpt.ui.theme.LotSpacing

// ── Capability bar ──────────────────────────────────────────────────────

@Composable
internal fun ChatComposerCapabilityBar(
    isClawMode: Boolean,
    isSaving: Boolean,
    modelLabel: String,
    reasoningEnabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LotSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(
            text = if (isClawMode) "Claw 本地" else "AI 对话",
            tone = if (isClawMode) StatusTone.Success else StatusTone.Primary
        )
        Text(
            text = modelLabel,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(LotRadius.md))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (reasoningEnabled) {
            StatusPill(text = "思考", tone = StatusTone.Neutral)
        }
        if (isSaving) {
            StatusPill(text = "生成中", tone = StatusTone.Primary)
        }
    }
}

// ── Action enum & icons ─────────────────────────────────────────────────

internal enum class ComposerAction {
    Send,
    Execute,
    Stop
}

@Composable
internal fun ComposerAttachmentIcon(color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.20f, size.height * 0.24f),
            size = Size(size.width * 0.60f, size.height * 0.52f),
            style = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.34f),
            end = Offset(size.width * 0.50f, size.height * 0.66f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.34f, size.height * 0.50f),
            end = Offset(size.width * 0.66f, size.height * 0.50f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun ComposerActionIcon(action: ComposerAction, color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        when (action) {
            ComposerAction.Send -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.22f, size.height * 0.50f),
                    end = Offset(size.width * 0.78f, size.height * 0.50f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.58f, size.height * 0.30f),
                    end = Offset(size.width * 0.78f, size.height * 0.50f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.58f, size.height * 0.70f),
                    end = Offset(size.width * 0.78f, size.height * 0.50f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round
                )
            }
            ComposerAction.Execute -> {
                drawLine(color, Offset(size.width * 0.58f, size.height * 0.12f), Offset(size.width * 0.34f, size.height * 0.52f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.52f), Offset(size.width * 0.52f, size.height * 0.52f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.52f, size.height * 0.52f), Offset(size.width * 0.42f, size.height * 0.88f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.42f, size.height * 0.88f), Offset(size.width * 0.72f, size.height * 0.42f), stroke.width, cap = StrokeCap.Round)
            }
            ComposerAction.Stop -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.28f),
                    size = Size(size.width * 0.44f, size.height * 0.44f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
    }
}

// ── Thinking indicator ──────────────────────────────────────────────────

@Composable
internal fun ThinkingIndicator(
    modelLabel: String,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (reduceMotion) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.7f))
                )
            }
        } else {
            val transition = rememberInfiniteTransition(label = "thinking")
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(index * 160)
                    ),
                    label = "thinking-dot-$index"
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = alpha))
                )
            }
        }
        Text(
            text = "$modelLabel · 思考中",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Document MIME types ─────────────────────────────────────────────────

internal fun documentMimeTypes(): Array<String> {
    return arrayOf(
        "text/plain",
        "text/markdown",
        "text/csv",
        "application/json",
        "application/xml",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "*/*"
    )
}

// ── Recording control card ──────────────────────────────────────────────

@Composable
internal fun RecordingControlCard(
    startDelaySeconds: Int,
    maxDurationSeconds: Int,
    countdownSeconds: Int,
    elapsedSeconds: Int,
    isRecording: Boolean,
    onStartDelaySelected: (Int) -> Unit,
    onDurationSelected: (Int) -> Unit,
    onStart: () -> Unit,
    onStopAndKeep: () -> Unit,
    onCancel: () -> Unit
) {
    val isWaiting = countdownSeconds > 0 && !isRecording
    val status = when {
        isRecording -> "录音中 ${formatDuration(elapsedSeconds)} / ${formatDuration(maxDurationSeconds)}"
        isWaiting -> "${countdownSeconds} 秒后开始录音"
        else -> "准备录音，结束后会生成待发送附件"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AppTestTags.CHAT_RECORDING_PANEL)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(LotRadius.md)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "录音控制",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isRecording) {
                StatusPill("REC", tone = StatusTone.Success)
            }
        }

        LinearProgressIndicator(
            progress = {
                if (isRecording) {
                    elapsedSeconds.toFloat() / maxDurationSeconds.toFloat()
                } else {
                    0f
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        ChoiceRow(
            label = "多久开始",
            choices = listOf(0, 3, 5, 10),
            selected = startDelaySeconds,
            enabled = !isRecording && !isWaiting,
            formatter = { if (it == 0) "立即" else "${it}s" },
            onSelected = onStartDelaySelected
        )
        ChoiceRow(
            label = "多久结束",
            choices = listOf(10, 30, 60, 120),
            selected = maxDurationSeconds,
            enabled = !isRecording && !isWaiting,
            formatter = { "${it}s" },
            onSelected = onDurationSelected
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording || isWaiting) {
                Button(
                    modifier = Modifier.testTag(AppTestTags.CHAT_RECORDING_STOP_KEEP),
                    onClick = onStopAndKeep,
                    enabled = isRecording
                ) {
                    Text("结束并保留")
                }
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
            } else {
                Button(
                    modifier = Modifier.testTag(AppTestTags.CHAT_RECORDING_START),
                    onClick = onStart
                ) {
                    Text("开始")
                }
                TextButton(onClick = onCancel) {
                    Text("返回")
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    choices: List<Int>,
    selected: Int,
    enabled: Boolean,
    formatter: (Int) -> String,
    onSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = selected == choice,
                    enabled = enabled,
                    onClick = { onSelected(choice) },
                    label = { Text(formatter(choice)) }
                )
            }
        }
    }
}

internal fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}
