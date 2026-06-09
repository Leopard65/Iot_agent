package com.example.iotgpt.feature.agent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
        subtitle = "本地任务执行、权限确认与任务日志",
        trailing = {
            StatusPill("${uiState.tasks.size} 条日志", tone = StatusTone.Success)
        }
    ) {
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
            Text("暂无任务日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TaskLogRow(latestTask)
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
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
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
