package com.example.iotgpt.feature.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotgpt.core.components.AppPage
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone
import com.example.iotgpt.core.database.entity.AgentTaskEntity
import com.example.iotgpt.core.util.CapturedFile
import com.example.iotgpt.core.util.FileUtils
import com.example.iotgpt.ui.theme.LotColors
import com.example.iotgpt.ui.theme.LotRadius
import com.example.iotgpt.ui.theme.LotSpacing

/**
 * Local agent task screen for controlled on-device assistant actions.
 */
@Composable
fun AgentScreen(
    viewModel: AgentViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSmsConfirm by remember { mutableStateOf(false) }
    var showTaskHistory by remember { mutableStateOf(false) }
    var pendingCameraCapture by remember { mutableStateOf<CapturedFile?>(null) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showSmsConfirm = true
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capture = pendingCameraCapture
        if (success && capture != null) {
            viewModel.logCameraResult(capture.uri.toString(), capture.displayPath)
        } else {
            viewModel.logCameraFailure()
        }
        pendingCameraCapture = null
    }

    fun launchCameraCapture() {
        val capture = FileUtils.createImageCapture(context, child = "agent")
        pendingCameraCapture = capture
        cameraLauncher.launch(capture.uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            viewModel.logCameraFailure("相机权限被拒绝，无法拍照")
        }
    }

    if (showSmsConfirm) {
        AlertDialog(
            onDismissRequest = { showSmsConfirm = false },
            title = { Text("确认发送短信") },
            text = {
                Text("将向 ${uiState.phoneNumber} 发送测试短信。此操作必须由你手动确认。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSmsConfirm = false
                        viewModel.sendSms()
                    }
                ) {
                    Text("确认发送")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showTaskHistory) {
        AlertDialog(
            onDismissRequest = { showTaskHistory = false },
            title = { Text("历史任务日志") },
            text = {
                if (uiState.tasks.isEmpty()) {
                    Text("暂无任务日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.tasks,
                            key = { it.id }
                        ) { task ->
                            TaskLogRow(task)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaskHistory = false }) {
                    Text("关闭")
                }
            }
        )
    }

    AppPage(
        title = "Claw 本地智能体",
        subtitle = "把明确指令转成本机服务调用",
        trailing = {
            StatusPill("${uiState.tasks.size} 条日志", tone = StatusTone.Success)
        }
    ) {
        AgentCapabilityOverview(
            latestTask = uiState.tasks.firstOrNull(),
            totalCount = uiState.tasks.size
        )

        AgentRouteRadarCard()

        LatestTaskLogCard(
            latestTask = uiState.tasks.firstOrNull(),
            totalCount = uiState.tasks.size,
            onViewHistory = { showTaskHistory = true }
        )

        uiState.statusMessage?.let {
            AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AppLeapAgentCard(
            command = uiState.appLeapCommand,
            onCommandChanged = viewModel::updateAppLeapCommand,
            onExecute = { viewModel.executeAppLeap() }
        )

        SmsAgentCard(
            phoneNumber = uiState.phoneNumber,
            smsContent = uiState.smsContent,
            onPhoneChanged = viewModel::updatePhoneNumber,
            onContentChanged = viewModel::updateSmsContent,
            onExecute = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    showSmsConfirm = true
                } else {
                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                }
            }
        )

        CameraAgentCard(
            photoUri = uiState.lastPhotoUri,
            photoPath = uiState.lastPhotoPath,
            onExecute = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    launchCameraCapture()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )

        DownloadAgentCard(
            url = uiState.downloadUrl,
            progress = uiState.downloadProgress,
            onUrlChanged = viewModel::updateDownloadUrl,
            onExecute = viewModel::startDownload
        )
    }
}

private data class AgentCapabilitySummary(
    val title: String,
    val status: String,
    val detail: String,
    val color: Color
)

@Composable
private fun AgentCapabilityOverview(
    latestTask: AgentTaskEntity?,
    totalCount: Int
) {
    val capabilityItems = listOf(
        AgentCapabilitySummary(
            title = "短信",
            status = "二次确认",
            detail = "发送前保留手动安全确认",
            color = LotColors.Warning
        ),
        AgentCapabilitySummary(
            title = "拍照",
            status = "权限触发",
            detail = "调用系统相机并回写任务日志",
            color = LotColors.Ai
        ),
        AgentCapabilitySummary(
            title = "下载",
            status = "后台执行",
            detail = "WorkManager 下载完成后发通知",
            color = LotColors.Iot
        ),
        AgentCapabilitySummary(
            title = "App-Leap",
            status = "Intent",
            detail = "打开链接或交给系统搜索",
            color = MaterialTheme.colorScheme.tertiary
        )
    )
    val stateLabel = latestTask?.let { agentTaskStatusLabel(it.status) } ?: "待命"
    val stateTone = latestTask?.let { agentTaskStatusTone(it.status) } ?: StatusTone.Primary

    AppSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "本地能力面板",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Claw 只执行用户明确触发的系统能力，关键动作保留权限和确认步骤。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(stateLabel, tone = stateTone)
        }

        capabilityItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LotSpacing.sm)
            ) {
                rowItems.forEach { item ->
                    AgentCapabilityTile(
                        item = item,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Text(
            text = "累计记录 $totalCount 次工具调用，可在历史中追踪本地智能体链路。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AgentCapabilityTile(
    item: AgentCapabilitySummary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 92.dp)
            .clip(RoundedCornerShape(LotRadius.md))
            .background(item.color.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = item.color.copy(alpha = 0.28f),
                shape = RoundedCornerShape(LotRadius.md)
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            drawCircle(color = item.color.copy(alpha = 0.18f), radius = size.minDimension / 2f)
            drawCircle(color = item.color, radius = size.minDimension * 0.22f)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.status,
                style = MaterialTheme.typography.labelMedium,
                color = item.color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LatestTaskLogCard(
    latestTask: AgentTaskEntity?,
    totalCount: Int,
    onViewHistory: () -> Unit
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最新任务日志",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(
                enabled = totalCount > 0,
                onClick = onViewHistory
            ) {
                Text("查看历史")
            }
        }
        if (latestTask == null) {
            TerminalLogLine("[claw-agent] >> waiting for tool call _")
        } else {
            TaskStatusCard(task = latestTask)
        }
    }
}

@Composable
private fun AgentRouteRadarCard() {
    val routeColor = MaterialTheme.colorScheme.primary
    val nodeColors = listOf(
        LotColors.Claw,
        MaterialTheme.colorScheme.tertiary,
        LotColors.Warning,
        MaterialTheme.colorScheme.secondary
    )
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("执行链路", "语义输入会被解析为受控工具调用")
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            val nodes = listOf(
                Offset(size.width * 0.08f, size.height * 0.58f),
                Offset(size.width * 0.36f, size.height * 0.28f),
                Offset(size.width * 0.64f, size.height * 0.54f),
                Offset(size.width * 0.92f, size.height * 0.34f)
            )
            val path = Path().apply {
                moveTo(nodes[0].x, nodes[0].y)
                cubicTo(
                    size.width * 0.20f,
                    size.height * 0.08f,
                    size.width * 0.27f,
                    size.height * 0.72f,
                    nodes[1].x,
                    nodes[1].y
                )
                cubicTo(
                    size.width * 0.47f,
                    size.height * 0.02f,
                    size.width * 0.53f,
                    size.height * 0.88f,
                    nodes[2].x,
                    nodes[2].y
                )
                cubicTo(
                    size.width * 0.76f,
                    size.height * 0.14f,
                    size.width * 0.82f,
                    size.height * 0.70f,
                    nodes[3].x,
                    nodes[3].y
                )
            }
            drawPath(
                path = path,
                color = routeColor,
                style = Stroke(width = 3.dp.toPx())
            )
            nodes.forEachIndexed { index, offset ->
                val color = nodeColors[index % nodeColors.size]
                drawCircle(color = color.copy(alpha = 0.18f), radius = 16.dp.toPx(), center = offset)
                drawCircle(color = color, radius = 6.dp.toPx(), center = offset)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("语义", "解析", "调度", "系统").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppLeapAgentCard(
    command: String,
    onCommandChanged: (String) -> Unit,
    onExecute: () -> Unit
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("App-Leap 联动", "打开链接，或把关键词交给系统搜索")
        OutlinedTextField(
            value = command,
            onValueChange = onCommandChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("链接或搜索词") },
            placeholder = { Text("https://example.com 或 今日天气") }
        )
        Button(onClick = onExecute) {
            Text("执行跃迁")
        }
    }
}

@Composable
private fun SmsAgentCard(
    phoneNumber: String,
    smsContent: String,
    onPhoneChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onExecute: () -> Unit
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("安全短信能力", "需要 SEND_SMS，执行前必须二次确认")
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = onPhoneChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("测试手机号") }
        )
        OutlinedTextField(
            value = smsContent,
            onValueChange = onContentChanged,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text("短信内容") }
        )
        Button(onClick = onExecute) {
            Text("发送短信")
        }
    }
}

@Composable
private fun CameraAgentCard(
    photoUri: String?,
    photoPath: String?,
    onExecute: () -> Unit
) {
    val context = LocalContext.current
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("自动拍照能力", "需要 CAMERA，用户点击后调用系统相机")
        Button(onClick = onExecute) {
            Text("执行拍照")
        }
        photoUri?.let { rawUri ->
            val bitmap = remember(rawUri) {
                runCatching {
                    context.contentResolver.openInputStream(rawUri.toUri())?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "拍照预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                )
            } ?: Text("照片已记录，预览暂不可用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            photoPath?.let {
                Text("保存位置：$it", maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("访问 URI：$rawUri", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DownloadAgentCard(
    url: String,
    progress: Int,
    onUrlChanged: (String) -> Unit,
    onExecute: () -> Unit
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("下载服务能力", "使用 WorkManager 下载，完成后触发通知")
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("下载 URL") }
        )
        SectorProgressView(progress)
        Text("进度 $progress%")
        Button(onClick = onExecute) {
            Text("开始下载")
        }
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill("可执行", tone = StatusTone.Primary)
        }
    }
}

@Composable
private fun TaskStatusCard(task: AgentTaskEntity) {
    val tone = agentTaskStatusTone(task.status)
    val borderColor = when (task.status.lowercase()) {
        "completed" -> MaterialTheme.colorScheme.primary
        "running" -> MaterialTheme.colorScheme.tertiary
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, borderColor.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = agentTaskTypeLabel(task.type),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = task.type,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(agentTaskStatusLabel(task.status), tone = tone)
        }
        Text(
            text = task.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TerminalLogLine(
    text: String,
    padded: Boolean = true
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(LotRadius.md))
            .then(if (padded) Modifier.padding(10.dp) else Modifier),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SectorProgressView(progress: Int) {
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val columns = 20
        val rows = 5
        val gap = 3.dp.toPx()
        val cellWidth = (size.width - gap * (columns - 1)) / columns
        val cellHeight = (size.height - gap * (rows - 1)) / rows
        val activeCells = (columns * rows * progress.coerceIn(0, 100) / 100f).toInt()

        repeat(rows) { row ->
            repeat(columns) { column ->
                val index = row * columns + column
                drawRoundRect(
                    color = if (index < activeCells) activeColor else trackColor,
                    topLeft = Offset(
                        x = column * (cellWidth + gap),
                        y = row * (cellHeight + gap)
                    ),
                    size = Size(cellWidth, cellHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun TaskLogRow(task: AgentTaskEntity) {
    TaskStatusCard(task = task)
}

private fun agentTaskTypeLabel(type: String): String {
    return when (type) {
        "sms" -> "短信任务"
        "camera" -> "拍照任务"
        "download" -> "下载任务"
        "document" -> "文档采集"
        "audio" -> "录音采集"
        "app-leap" -> "跨应用任务"
        "unknown" -> "未识别任务"
        else -> type
    }
}

private fun agentTaskStatusLabel(status: String): String {
    return when (status.lowercase()) {
        "completed" -> "成功"
        "running" -> "执行中"
        "failed" -> "失败"
        "cancelled" -> "已取消"
        else -> status
    }
}

private fun agentTaskStatusTone(status: String): StatusTone {
    return when (status.lowercase()) {
        "completed" -> StatusTone.Success
        "running" -> StatusTone.Primary
        else -> StatusTone.Neutral
    }
}
