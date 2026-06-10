package com.example.iotgpt.feature.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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

/**
 * Local agent task screen for safe classroom demonstrations.
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
        title = "Claw 智能体",
        subtitle = "本地工具调度、系统 Intent 与硬件能力演示",
        trailing = {
            StatusPill("${uiState.tasks.size} 条日志", tone = StatusTone.Success)
        }
    ) {
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

@Composable
private fun LatestTaskLogCard(
    latestTask: AgentTaskEntity?,
    totalCount: Int,
    onViewHistory: () -> Unit
) {
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
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
            TerminalTaskLog(latestTask)
        }
    }
}

@Composable
private fun AgentRouteRadarCard() {
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
                color = Color(0xFF4F46E5),
                style = Stroke(width = 3.dp.toPx())
            )
            nodes.forEachIndexed { index, offset ->
                val color = when (index) {
                    0 -> Color(0xFF22C55E)
                    1 -> Color(0xFF38BDF8)
                    2 -> Color(0xFFF59E0B)
                    else -> Color(0xFFEC4899)
                }
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
            placeholder = { Text("https://example.com 或 MQTT 调试") }
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
        CardTitle("安全短信演示", "需要 SEND_SMS，执行前必须二次确认")
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
        CardTitle("自动拍照演示", "需要 CAMERA，用户点击后调用系统相机")
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
        CardTitle("下载服务演示", "使用 WorkManager 下载，完成后触发通知")
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            StatusPill("可执行", tone = StatusTone.Primary)
        }
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TerminalTaskLog(task: AgentTaskEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF05080E), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TerminalLogLine("[claw-agent] >> tool=${task.type} status=${task.status}", padded = false)
        TerminalLogLine("[system] >> ${task.description}", padded = false)
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
            .background(Color(0xFF05080E), RoundedCornerShape(8.dp))
            .then(if (padded) Modifier.padding(10.dp) else Modifier),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFF00FF66),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SectorProgressView(progress: Int) {
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
                    color = if (index < activeCells) Color(0xFF00E5FF) else Color(0xFF1E293B),
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(task.type, fontWeight = FontWeight.SemiBold)
            StatusPill(task.status)
        }
        Text(
            text = task.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
