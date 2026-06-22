package com.example.iotgpt.feature.chat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iotgpt.core.testing.AppTestTags
import com.example.iotgpt.ui.theme.LotColors
import com.example.iotgpt.ui.theme.LotRadius

internal enum class ClawPanelMode {
    Download,
    Sms,
    AppLeap
}

internal enum class ClawPanelAction {
    Photo,
    Document,
    Audio,
    Download,
    Sms,
    AppLeap
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClawToolPanel(
    activeMode: ClawPanelMode?,
    downloadInput: String,
    smsPhoneInput: String,
    smsContentInput: String,
    appLeapInput: String,
    onDownloadInputChange: (String) -> Unit,
    onSmsPhoneChange: (String) -> Unit,
    onSmsContentChange: (String) -> Unit,
    onAppLeapInputChange: (String) -> Unit,
    onModeSelected: (ClawPanelMode) -> Unit,
    onPhoto: () -> Unit,
    onDocument: () -> Unit,
    onAudio: () -> Unit,
    onDownloadSubmit: () -> Unit,
    onSmsSubmit: () -> Unit,
    onAppLeapSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AppTestTags.CLAW_TOOL_PANEL)
            .background(
                color = LotColors.Claw.copy(alpha = 0.08f),
                shape = RoundedCornerShape(LotRadius.md)
            )
            .border(
                width = 1.dp,
                color = LotColors.Claw.copy(alpha = 0.22f),
                shape = RoundedCornerShape(LotRadius.md)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClawToolButton(
                label = "拍照",
                action = ClawPanelAction.Photo,
                onClick = onPhoto
            )
            ClawToolButton(
                label = "文档",
                action = ClawPanelAction.Document,
                onClick = onDocument
            )
            ClawToolButton(
                label = "录音",
                action = ClawPanelAction.Audio,
                onClick = onAudio
            )
            ClawToolButton(
                label = "下载",
                action = ClawPanelAction.Download,
                selected = activeMode == ClawPanelMode.Download,
                testTag = AppTestTags.CLAW_TOOL_DOWNLOAD,
                onClick = { onModeSelected(ClawPanelMode.Download) }
            )
            ClawToolButton(
                label = "短信",
                action = ClawPanelAction.Sms,
                selected = activeMode == ClawPanelMode.Sms,
                testTag = AppTestTags.CLAW_TOOL_SMS,
                onClick = { onModeSelected(ClawPanelMode.Sms) }
            )
            ClawToolButton(
                label = "App-Leap",
                action = ClawPanelAction.AppLeap,
                selected = activeMode == ClawPanelMode.AppLeap,
                testTag = AppTestTags.CLAW_TOOL_APP_LEAP,
                onClick = { onModeSelected(ClawPanelMode.AppLeap) }
            )
        }
        when (activeMode) {
            ClawPanelMode.Download -> ClawSingleInputRow(
                value = downloadInput,
                onValueChange = onDownloadInputChange,
                placeholder = "https://example.com/file.zip",
                buttonText = "下载",
                enabled = downloadInput.isNotBlank(),
                onSubmit = onDownloadSubmit
            )
            ClawPanelMode.Sms -> ClawSmsInputRow(
                phone = smsPhoneInput,
                content = smsContentInput,
                onPhoneChange = onSmsPhoneChange,
                onContentChange = onSmsContentChange,
                onSubmit = onSmsSubmit
            )
            ClawPanelMode.AppLeap -> ClawSingleInputRow(
                value = appLeapInput,
                onValueChange = onAppLeapInputChange,
                placeholder = "打开 Wi-Fi / 搜索天气 / https://...",
                buttonText = "跃迁",
                enabled = appLeapInput.isNotBlank(),
                onSubmit = onAppLeapSubmit
            )
            null -> Unit
        }
    }
}

@Composable
private fun ClawToolButton(
    label: String,
    action: ClawPanelAction,
    selected: Boolean = false,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val container = if (selected) {
        LotColors.Claw
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (selected) {
        LotColors.OnClaw
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(64.dp)
            .widthIn(min = 88.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .background(container, RoundedCornerShape(LotRadius.md))
            .border(
                width = 1.dp,
                color = if (selected) LotColors.Claw else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(LotRadius.md)
            ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp, vertical = 8.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ClawToolIcon(action = action, color = content)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ClawSingleInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    buttonText: String,
    enabled: Boolean,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            singleLine = true,
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (enabled) onSubmit() })
        )
        Button(
            onClick = onSubmit,
            enabled = enabled,
            shape = RoundedCornerShape(LotRadius.md)
        ) {
            Text(buttonText)
        }
    }
}

@Composable
private fun ClawSmsInputRow(
    phone: String,
    content: String,
    onPhoneChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("手机号") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                singleLine = true,
                placeholder = { Text("短信内容") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (phone.isNotBlank()) onSubmit() })
            )
            Button(
                onClick = onSubmit,
                enabled = phone.isNotBlank(),
                shape = RoundedCornerShape(LotRadius.md)
            ) {
                Text("发送")
            }
        }
    }
}

@Composable
private fun ClawToolIcon(
    action: ClawPanelAction,
    color: Color
) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        when (action) {
            ClawPanelAction.Photo -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.28f),
                    size = Size(size.width * 0.76f, size.height * 0.52f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke
                )
                drawCircle(color, radius = size.minDimension * 0.14f, center = center, style = stroke)
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.28f), Offset(size.width * 0.36f, size.height * 0.16f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.36f, size.height * 0.16f), Offset(size.width * 0.58f, size.height * 0.16f), stroke.width, cap = StrokeCap.Round)
            }
            ClawPanelAction.Document -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.24f, size.height * 0.12f),
                    size = Size(size.width * 0.52f, size.height * 0.76f),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = stroke
                )
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.40f), Offset(size.width * 0.66f, size.height * 0.40f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.56f), Offset(size.width * 0.66f, size.height * 0.56f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.72f), Offset(size.width * 0.54f, size.height * 0.72f), stroke.width, cap = StrokeCap.Round)
            }
            ClawPanelAction.Audio -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.36f, size.height * 0.12f),
                    size = Size(size.width * 0.28f, size.height * 0.50f),
                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                    style = stroke
                )
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.48f), Offset(size.width * 0.22f, size.height * 0.56f), stroke.width, cap = StrokeCap.Round)
                drawArc(color, 0f, 180f, false, topLeft = Offset(size.width * 0.22f, size.height * 0.42f), size = Size(size.width * 0.56f, size.height * 0.34f), style = stroke)
                drawLine(color, Offset(size.width * 0.50f, size.height * 0.76f), Offset(size.width * 0.50f, size.height * 0.88f), stroke.width, cap = StrokeCap.Round)
            }
            ClawPanelAction.Download -> {
                drawLine(color, Offset(size.width * 0.50f, size.height * 0.14f), Offset(size.width * 0.50f, size.height * 0.62f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.32f, size.height * 0.46f), Offset(size.width * 0.50f, size.height * 0.64f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.68f, size.height * 0.46f), Offset(size.width * 0.50f, size.height * 0.64f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.84f), Offset(size.width * 0.78f, size.height * 0.84f), stroke.width, cap = StrokeCap.Round)
            }
            ClawPanelAction.Sms -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.14f, size.height * 0.18f),
                    size = Size(size.width * 0.72f, size.height * 0.56f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = stroke
                )
                drawLine(color, Offset(size.width * 0.32f, size.height * 0.74f), Offset(size.width * 0.26f, size.height * 0.88f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.42f), Offset(size.width * 0.66f, size.height * 0.42f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.56f), Offset(size.width * 0.58f, size.height * 0.56f), stroke.width, cap = StrokeCap.Round)
            }
            ClawPanelAction.AppLeap -> {
                drawCircle(color, radius = size.minDimension * 0.30f, center = center, style = stroke)
                drawLine(color, Offset(size.width * 0.48f, size.height * 0.18f), Offset(size.width * 0.76f, size.height * 0.18f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.76f, size.height * 0.18f), Offset(size.width * 0.76f, size.height * 0.46f), stroke.width, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.74f, size.height * 0.20f), Offset(size.width * 0.54f, size.height * 0.40f), stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}
