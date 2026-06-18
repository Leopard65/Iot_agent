package com.example.iotgpt.feature.chat.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotgpt.core.components.AppPage
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone
import com.example.iotgpt.core.database.entity.AgentTaskEntity
import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.core.preferences.ModelProfile
import com.example.iotgpt.core.util.AttachmentPreview
import com.example.iotgpt.core.util.AudioRecorder
import com.example.iotgpt.core.util.CapturedFile
import com.example.iotgpt.core.util.FileUtils
import com.example.iotgpt.core.testing.AppTestTags
import com.example.iotgpt.feature.chat.ClawCommand
import com.example.iotgpt.feature.chat.parseClawCommand
import com.example.iotgpt.feature.agent.ui.AgentViewModel
import com.example.iotgpt.ui.theme.LotColors
import com.example.iotgpt.ui.theme.LotMotion
import com.example.iotgpt.ui.theme.LotRadius
import com.example.iotgpt.ui.theme.LotSpacing
import com.example.iotgpt.ui.theme.rememberReduceMotion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

/**
 * Room-backed chat screen with local conversation history.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    agentViewModel: AgentViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val audioRecorder = remember { AudioRecorder(context) }
    val messageListState = rememberLazyListState()
    val conversationFade = remember { Animatable(1f) }
    LaunchedEffect(uiState.currentConversationId) {
        if (reduceMotion) {
            conversationFade.snapTo(1f)
        } else {
            conversationFade.snapTo(0f)
            conversationFade.animateTo(1f, animationSpec = tween(LotMotion.normal))
        }
    }
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingClawCapture by remember { mutableStateOf<CapturedFile?>(null) }
    var pendingClawSms by remember { mutableStateOf<ClawSmsCommand?>(null) }
    var pendingClawCameraRequest by rememberSaveable { mutableStateOf(false) }
    var pendingClawDocumentRequest by rememberSaveable { mutableStateOf(false) }
    var pendingClawAudioRequest by rememberSaveable { mutableStateOf(false) }
    var showClawSmsConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
    var attachmentMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showRecordingPanel by rememberSaveable { mutableStateOf(false) }
    var recordingStartDelaySeconds by rememberSaveable { mutableIntStateOf(0) }
    var recordingMaxDurationSeconds by rememberSaveable { mutableIntStateOf(30) }
    var recordingCountdownSeconds by rememberSaveable { mutableIntStateOf(0) }
    var recordingElapsedSeconds by rememberSaveable { mutableIntStateOf(0) }
    var pendingRecordingStart by rememberSaveable { mutableStateOf(false) }
    var visibleMessageCount by rememberSaveable(uiState.currentConversationId) {
        mutableIntStateOf(10)
    }
    var isLoadingOlderMessages by rememberSaveable(uiState.currentConversationId) {
        mutableStateOf(false)
    }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var notificationPromptDismissed by rememberSaveable { mutableStateOf(false) }
    var followLatest by rememberSaveable(uiState.currentConversationId) { mutableStateOf(false) }
    var followRequiresSaving by rememberSaveable(uiState.currentConversationId) { mutableStateOf(false) }
    var followSawSaving by rememberSaveable(uiState.currentConversationId) { mutableStateOf(false) }
    var followStartMessageCount by rememberSaveable(uiState.currentConversationId) { mutableIntStateOf(0) }
    val visibleMessages = uiState.messages.takeLast(visibleMessageCount)

    DisposableEffect(Unit) {
        onDispose { audioRecorder.cancel() }
    }

    LaunchedEffect(followLatest, visibleMessages.size, uiState.messages.size, uiState.isSaving) {
        if (!followLatest) return@LaunchedEffect
        if (visibleMessages.isNotEmpty()) {
            messageListState.scrollToItem(0)
        }
        if (uiState.isSaving) {
            followSawSaving = true
        }

        val hasNewMessages = uiState.messages.size > followStartMessageCount
        val canRelease = if (followRequiresSaving) {
            followSawSaving && !uiState.isSaving
        } else {
            hasNewMessages
        }
        if (canRelease) {
            delay(220)
            if (visibleMessages.isNotEmpty()) {
                messageListState.scrollToItem(0)
            }
            followLatest = false
            followRequiresSaving = false
            followSawSaving = false
        }
    }

    fun beginFollowLatest(requiresSaving: Boolean) {
        followLatest = true
        followRequiresSaving = requiresSaving
        followSawSaving = false
        followStartMessageCount = uiState.messages.size
    }

    fun prepareAttachment(type: String, uri: Uri): Boolean {
        if (FileUtils.isTooLarge(context, uri)) {
            viewModel.showError("文件超过 10MB，暂不支持上传")
            return false
        }
        val info = FileUtils.queryAttachmentInfo(context, uri)
        val textPreview = if (type == "document") {
            FileUtils.readTextPreviewIfPossible(context, uri)
        } else {
            null
        }
        val json = FileUtils.buildAttachmentJson(
            context = context,
            type = type,
            uri = uri,
            textPreview = textPreview
        )
        val typeLabel = when (type) {
            "image" -> "图片"
            "audio" -> "录音"
            else -> "文档"
        }
        val sizeLabel = FileUtils.formatSize(info.sizeBytes)
        val mimeType = info.mimeType ?: "未知"
        val content = "用户上传$typeLabel：${info.displayName}，大小 $sizeLabel，类型 $mimeType。"
        pendingAttachment = PendingAttachment(
            type = type,
            json = json,
            displayName = info.displayName,
            sizeLabel = sizeLabel,
            mimeType = info.mimeType,
            baseContent = content
        )
        viewModel.showNotice("$typeLabel 已添加，可直接发送，也可以继续输入说明后一起发送")
        return true
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val clawCapture = pendingClawCapture
        val uri = pendingCameraUri
        when {
            success && clawCapture != null -> {
                agentViewModel.logCameraResult(
                    uri = clawCapture.uri.toString(),
                    displayPath = clawCapture.displayPath
                )
                viewModel.addClawPhotoResult(clawCapture.uri)
            }
            success && uri != null -> {
                prepareAttachment("image", uri)
            }
            clawCapture != null -> {
                agentViewModel.logCameraFailure()
                viewModel.addClawResult("Claw 拍照已取消或失败。")
            }
            else -> {
                viewModel.showError("拍照已取消或失败")
            }
        }
        pendingCameraUri = null
        pendingClawCapture = null
    }

    fun launchCameraCapture() {
        val uri = FileUtils.createImageUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    fun launchClawCameraCapture() {
        val capture = FileUtils.createImageCapture(context, "claw_images")
        pendingClawCapture = capture
        cameraLauncher.launch(capture.uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingClawCameraRequest) {
                pendingClawCameraRequest = false
                launchClawCameraCapture()
            } else {
                launchCameraCapture()
            }
        } else {
            val wasClawRequest = pendingClawCameraRequest
            pendingClawCameraRequest = false
            if (wasClawRequest) {
                agentViewModel.logCameraFailure("相机权限被拒绝，Claw 无法执行自动拍照")
                viewModel.addClawResult("Claw 拍照未执行：相机权限被拒绝。")
            } else {
                viewModel.showError("相机权限被拒绝，无法拍照")
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val wasClawRequest = pendingClawDocumentRequest
        pendingClawDocumentRequest = false
        if (uri == null) {
            if (wasClawRequest) {
                viewModel.logClawCommand(
                    type = "document",
                    status = "cancelled",
                    description = "文档采集已取消：未选择文件"
                )
                viewModel.addClawResult("Claw 文档采集已取消：未选择文件。")
            } else {
                viewModel.showError("未选择文档")
            }
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val prepared = prepareAttachment("document", uri)
        if (wasClawRequest) {
            if (prepared) {
                viewModel.logClawCommand(
                    type = "document",
                    status = "completed",
                    description = "文档采集完成，附件已放入待发送区"
                )
                viewModel.addClawResult("Claw 已打开系统文件选择器并完成文档采集，附件已放入待发送区。")
            } else {
                viewModel.logClawCommand(
                    type = "document",
                    status = "failed",
                    description = "文档采集失败：文件超过 10MB"
                )
                viewModel.addClawResult("Claw 文档采集失败：文件超过 10MB。")
            }
        }
    }

    fun launchDocumentSelection(fromClaw: Boolean = false) {
        pendingClawDocumentRequest = fromClaw
        documentLauncher.launch(documentMimeTypes())
    }

    fun beginRecording() {
        runCatching {
            audioRecorder.start()
            isRecording = true
            recordingCountdownSeconds = 0
            recordingElapsedSeconds = 0
            showRecordingPanel = true
        }.onFailure {
            val message = "录音启动失败：${it.message ?: "请检查麦克风权限"}"
            if (pendingClawAudioRequest) {
                pendingClawAudioRequest = false
                viewModel.logClawCommand(
                    type = "audio",
                    status = "failed",
                    description = "录音启动失败：${it.message ?: "请检查麦克风权限"}"
                )
                viewModel.addClawResult("Claw 音频采集未执行：$message")
            } else {
                viewModel.showError(message)
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingRecordingStart) {
                pendingRecordingStart = false
                if (recordingStartDelaySeconds > 0) {
                    recordingCountdownSeconds = recordingStartDelaySeconds
                } else {
                    beginRecording()
                }
            }
        } else {
            pendingRecordingStart = false
            if (pendingClawAudioRequest) {
                pendingClawAudioRequest = false
                viewModel.logClawCommand(
                    type = "audio",
                    status = "failed",
                    description = "录音权限被拒绝，音频采集未执行"
                )
                viewModel.addClawResult("Claw 音频采集未执行：录音权限被拒绝。")
            } else {
                viewModel.showError("录音权限被拒绝，无法采集音频")
            }
        }
    }

    fun executeClawSms(command: ClawSmsCommand) {
        agentViewModel.updatePhoneNumber(command.phoneNumber)
        agentViewModel.updateSmsContent(command.content)
        agentViewModel.sendSms()
        viewModel.addClawExchange(
            userContent = command.userContent,
            resultContent = "Claw 已调用本地短信能力，目标号码：${command.phoneNumber}。"
        )
        pendingClawSms = null
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val command = pendingClawSms
        if (granted && command != null) {
            showClawSmsConfirm = true
        } else {
            pendingClawSms = null
            showClawSmsConfirm = false
            if (command != null) {
                viewModel.logClawCommand(
                    type = "sms",
                    status = "failed",
                    description = "短信任务未执行：短信权限被拒绝，目标号码 ${command.phoneNumber}"
                )
                viewModel.addClawExchange(
                    userContent = command.userContent,
                    resultContent = "Claw 短信任务未执行：短信权限被拒绝。"
                )
            } else {
                viewModel.logClawCommand(
                    type = "sms",
                    status = "failed",
                    description = "短信任务未执行：短信权限被拒绝"
                )
                viewModel.addClawResult("Claw 短信任务未执行：短信权限被拒绝。")
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            viewModel.showError("通知权限被拒绝，AI 回复完成时将只显示 App 内提示")
        }
        notificationPromptDismissed = true
    }

    fun requestCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun stopRecording(keep: Boolean) {
        if (!isRecording) {
            if (!keep) {
                showRecordingPanel = false
                recordingCountdownSeconds = 0
                if (pendingClawAudioRequest) {
                    pendingClawAudioRequest = false
                    viewModel.logClawCommand(
                        type = "audio",
                        status = "cancelled",
                        description = "音频采集已取消"
                    )
                    viewModel.addClawResult("Claw 音频采集已取消。")
                }
            }
            return
        }

        if (keep) {
            val uri = audioRecorder.stop()
            isRecording = false
            showRecordingPanel = false
            recordingElapsedSeconds = 0
            if (uri != null) {
                val prepared = prepareAttachment("audio", uri)
                if (pendingClawAudioRequest) {
                    if (prepared) {
                        viewModel.logClawCommand(
                            type = "audio",
                            status = "completed",
                            description = "录音采集完成，音频附件已放入待发送区"
                        )
                        viewModel.addClawResult("Claw 已完成录音采集，音频附件已放入待发送区。")
                    } else {
                        viewModel.logClawCommand(
                            type = "audio",
                            status = "failed",
                            description = "音频采集失败：文件超过 10MB"
                        )
                        viewModel.addClawResult("Claw 音频采集失败：文件超过 10MB。")
                    }
                    pendingClawAudioRequest = false
                }
            } else {
                if (pendingClawAudioRequest) {
                    pendingClawAudioRequest = false
                    viewModel.logClawCommand(
                        type = "audio",
                        status = "failed",
                        description = "音频采集失败：录音保存失败"
                    )
                    viewModel.addClawResult("Claw 音频采集失败：录音保存失败。")
                } else {
                    viewModel.showError("录音保存失败")
                }
            }
        } else {
            audioRecorder.cancel()
            isRecording = false
            showRecordingPanel = false
            recordingElapsedSeconds = 0
            recordingCountdownSeconds = 0
            if (pendingClawAudioRequest) {
                pendingClawAudioRequest = false
                viewModel.logClawCommand(
                    type = "audio",
                    status = "cancelled",
                    description = "音频采集已取消"
                )
                viewModel.addClawResult("Claw 音频采集已取消。")
            } else {
                viewModel.showNotice("录音已取消")
            }
        }
    }

    fun requestRecordingStart() {
        showRecordingPanel = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (recordingStartDelaySeconds > 0) {
                recordingCountdownSeconds = recordingStartDelaySeconds
            } else {
                beginRecording()
            }
        } else {
            pendingRecordingStart = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(recordingCountdownSeconds, isRecording) {
        if (recordingCountdownSeconds <= 0 || isRecording) return@LaunchedEffect
        delay(1000)
        val next = recordingCountdownSeconds - 1
        recordingCountdownSeconds = next
        if (next == 0) {
            beginRecording()
        }
    }

    LaunchedEffect(isRecording, recordingElapsedSeconds, recordingMaxDurationSeconds) {
        if (!isRecording) return@LaunchedEffect
        delay(1000)
        val next = recordingElapsedSeconds + 1
        recordingElapsedSeconds = next
        if (next >= recordingMaxDurationSeconds) {
            stopRecording(keep = true)
        }
    }

    fun requestClawCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchClawCameraCapture()
        } else {
            pendingClawCameraRequest = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun handleClawCommand(messageText: String) {
        when (val command = parseClawCommand(messageText)) {
            ClawCommand.Camera -> {
                viewModel.addClawUserCommand(messageText, "Claw 正在打开相机执行自动拍照...")
                requestClawCamera()
            }
            is ClawCommand.Download -> {
                if (command.url == null) {
                    viewModel.logClawCommand(
                        type = "download",
                        status = "failed",
                        description = "下载指令缺少可用 URL：$messageText"
                    )
                    viewModel.addClawExchange(
                        userContent = messageText,
                        resultContent = "Claw 未找到下载链接，请输入类似：下载 https://example.com/file.bin"
                    )
                    return
                }
                agentViewModel.updateDownloadUrl(command.url)
                agentViewModel.startDownload()
                viewModel.addClawExchange(
                    userContent = messageText,
                    resultContent = "Claw 已启动本地下载任务：${command.url}\n下载进度会通过系统任务与通知更新。"
                )
            }
            is ClawCommand.Sms -> {
                if (command.phoneNumber == null) {
                    viewModel.logClawCommand(
                        type = "sms",
                        status = "failed",
                        description = "短信指令缺少目标号码：$messageText"
                    )
                    viewModel.addClawExchange(
                        userContent = messageText,
                        resultContent = "Claw 未找到短信号码，请输入类似：短信 13800138000 内容"
                    )
                    return
                }
                val sms = ClawSmsCommand(
                    userContent = messageText,
                    phoneNumber = command.phoneNumber,
                    content = command.content.ifBlank { "lot 短信确认" }
                )
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    pendingClawSms = sms
                    showClawSmsConfirm = true
                } else {
                    pendingClawSms = sms
                    smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                }
            }
            ClawCommand.Document -> {
                viewModel.addClawUserCommand(messageText, "Claw 正在打开系统文件选择器采集文档...")
                launchDocumentSelection(fromClaw = true)
            }
            ClawCommand.Audio -> {
                pendingClawAudioRequest = true
                viewModel.addClawUserCommand(messageText, "Claw 正在调用麦克风执行录音采集...")
                requestRecordingStart()
            }
            is ClawCommand.AppLeap -> {
                agentViewModel.updateAppLeapCommand(command.command)
                agentViewModel.executeAppLeap { success, message ->
                    viewModel.addClawExchange(
                        userContent = messageText,
                        resultContent = if (success) {
                            "Claw 已执行系统 Intent：${command.command}\n可用于打开链接、系统设置、Wi-Fi/蓝牙页面，或发起浏览器搜索。"
                        } else {
                            "Claw 执行系统 Intent 失败：$message"
                        }
                    )
                }
            }
            ClawCommand.Unknown -> {
                viewModel.logClawCommand(
                    type = "unknown",
                    status = "failed",
                    description = "未识别到本地自动化指令：$messageText"
                )
                viewModel.addClawExchange(
                    userContent = messageText,
                    resultContent = "Claw 未识别到本地自动化指令。可试试：拍照、选择文档、录音、下载 https://...、短信 13800138000 内容、打开设置、打开 Wi-Fi、打开浏览器搜索天气。"
                )
            }
        }
    }

    fun submitMessage() {
        val messageText = draft.trim()
        val attachment = pendingAttachment
        when {
            attachment != null -> {
                beginFollowLatest(requiresSaving = true)
                val content = if (messageText.isBlank()) {
                    attachment.baseContent
                } else {
                    "$messageText\n\n${attachment.baseContent}"
                }
                viewModel.sendAttachment(content, attachment.json)
                pendingAttachment = null
                draft = ""
            }
            uiState.isClawMode && messageText.isNotBlank() -> {
                beginFollowLatest(requiresSaving = false)
                handleClawCommand(messageText)
                draft = ""
            }
            messageText.isNotBlank() -> {
                beginFollowLatest(requiresSaving = true)
                viewModel.sendMessage(messageText)
                draft = ""
            }
        }
    }

    if (showClawSmsConfirm && pendingClawSms != null) {
        val command = pendingClawSms
        AlertDialog(
            onDismissRequest = {
                val cancelled = pendingClawSms
                showClawSmsConfirm = false
                pendingClawSms = null
                if (cancelled != null) {
                    viewModel.logClawCommand(
                        type = "sms",
                        status = "cancelled",
                        description = "短信任务未执行：用户取消二次确认，目标号码 ${cancelled.phoneNumber}"
                    )
                    viewModel.addClawExchange(
                        userContent = cancelled.userContent,
                        resultContent = "Claw 短信任务未执行：用户取消二次确认。"
                    )
                }
            },
            title = { Text("确认发送短信") },
            text = {
                Text("将向 ${command?.phoneNumber.orEmpty()} 发送测试短信。此操作必须由你手动确认。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val confirmed = pendingClawSms
                        showClawSmsConfirm = false
                        if (confirmed != null) {
                            executeClawSms(confirmed)
                        }
                    }
                ) {
                    Text("确认发送")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val cancelled = pendingClawSms
                        showClawSmsConfirm = false
                        pendingClawSms = null
                        if (cancelled != null) {
                            viewModel.logClawCommand(
                                type = "sms",
                                status = "cancelled",
                                description = "短信任务未执行：用户取消二次确认，目标号码 ${cancelled.phoneNumber}"
                            )
                            viewModel.addClawExchange(
                                userContent = cancelled.userContent,
                                resultContent = "Claw 短信任务未执行：用户取消二次确认。"
                            )
                        }
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistoryDrawer(
                conversations = uiState.conversations,
                clawTasks = uiState.clawTasks,
                currentConversationId = uiState.currentConversationId,
                onSelect = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onDelete = viewModel::deleteConversation,
                onRename = viewModel::renameConversation,
                onNewConversation = {
                    viewModel.createConversation()
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        AppPage(
            title = uiState.currentConversation?.title ?: "lot 对话",
            subtitle = "",
            scrollable = false,
            showHeader = false,
        ) {
            val conversationPreview = uiState.currentConversation?.summary
                ?.takeIf { it.isNotBlank() }
                ?: uiState.currentConversation?.title
                ?: "新对话"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { scope.launch { drawerState.open() } }) {
                    Text("历史")
                }
                Text(
                    text = conversationPreview,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ClawModeSwitch(
                    enabled = uiState.isClawMode,
                    compact = true,
                    onCheckedChange = viewModel::setClawMode
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelQuickSwitcher(
                    profiles = uiState.modelProfiles,
                    activeProfile = uiState.activeModelProfile,
                    onSelect = viewModel::activateModelProfile,
                    modifier = Modifier.weight(1f)
                )
                uiState.activeModelProfile?.takeIf { it.supportsReasoning }?.let { profile ->
                    ReasoningQuickToggle(
                        enabled = profile.reasoningEnabled,
                        onCheckedChange = viewModel::updateActiveReasoningEnabled
                    )
                }
                TextButton(onClick = viewModel::createConversation) {
                    Text("新建")
                }
            }

            uiState.errorMessage?.let { error ->
                AutoDismissMessageCard(
                    message = error,
                    isError = true,
                    onDismiss = viewModel::dismissError
                )
            }

            uiState.noticeMessage?.let { notice ->
                AutoDismissMessageCard(
                    message = notice,
                    isError = false,
                    onDismiss = viewModel::dismissNotice
                )
            }

            if (shouldShowNotificationPrompt(context) && !notificationPromptDismissed) {
                AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "允许通知后，AI 回复完成时会出现在系统消息栏。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            }
                        ) {
                            Text("允许通知")
                        }
                        TextButton(onClick = { notificationPromptDismissed = true }) {
                            Text("稍后")
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = conversationFade.value }
                    .testTag(AppTestTags.CHAT_MESSAGES),
                state = messageListState,
                reverseLayout = uiState.messages.isNotEmpty(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = !followLatest
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        EmptyChatCard(
                            onExampleSelected = { example ->
                                draft = example
                            }
                        )
                    }
                } else {
                    items(
                        items = visibleMessages.asReversed(),
                        key = { it.id }
                    ) { message ->
                        Box(
                            modifier = if (reduceMotion) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .animateItem(fadeInSpec = tween(LotMotion.normal))
                            }
                        ) {
                            MessageBubble(
                                message = message,
                                modelLabel = uiState.activeModelProfile?.model ?: "模型",
                                reduceMotion = reduceMotion,
                                onCopy = { text ->
                                    clipboardManager.setPrimaryClip(
                                        ClipData.newPlainText("lot message", text)
                                    )
                                    viewModel.showNotice("消息已复制")
                                },
                                onRetry = viewModel::retryAssistantMessage
                            )
                        }
                    }
                    if (visibleMessages.size < uiState.messages.size) {
                        item(key = "load-older-messages") {
                            LaunchedEffect(visibleMessages.size, uiState.messages.size) {
                                if (!isLoadingOlderMessages) {
                                    isLoadingOlderMessages = true
                                    delay(450)
                                    visibleMessageCount = (visibleMessageCount + 10)
                                        .coerceAtMost(uiState.messages.size)
                                    isLoadingOlderMessages = false
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = "正在加载更早消息...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            AppSectionCard(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                val accent by animateColorAsState(
                    targetValue = if (uiState.isClawMode) LotColors.Claw else MaterialTheme.colorScheme.primary,
                    animationSpec = tween(if (reduceMotion) 0 else LotMotion.normal),
                    label = "claw-accent"
                )
                var lastPendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
                LaunchedEffect(pendingAttachment) {
                    pendingAttachment?.let { lastPendingAttachment = it }
                }
                AnimatedVisibility(
                    visible = pendingAttachment != null,
                    enter = if (reduceMotion) EnterTransition.None else expandVertically(animationSpec = tween(LotMotion.normal)) + fadeIn(animationSpec = tween(LotMotion.normal)),
                    exit = if (reduceMotion) ExitTransition.None else shrinkVertically(animationSpec = tween(LotMotion.fast)) + fadeOut(animationSpec = tween(LotMotion.fast))
                ) {
                    (pendingAttachment ?: lastPendingAttachment)?.let { attachment ->
                        PendingAttachmentCard(
                            attachment = attachment,
                            onRemove = {
                                pendingAttachment = null
                                viewModel.showNotice("附件已移除")
                            }
                        )
                    }
                }
                if (showRecordingPanel || isRecording || recordingCountdownSeconds > 0) {
                    RecordingControlCard(
                        startDelaySeconds = recordingStartDelaySeconds,
                        maxDurationSeconds = recordingMaxDurationSeconds,
                        countdownSeconds = recordingCountdownSeconds,
                        elapsedSeconds = recordingElapsedSeconds,
                        isRecording = isRecording,
                        onStartDelaySelected = { recordingStartDelaySeconds = it },
                        onDurationSelected = { recordingMaxDurationSeconds = it },
                        onStart = ::requestRecordingStart,
                        onStopAndKeep = { stopRecording(keep = true) },
                        onCancel = { stopRecording(keep = false) }
                    )
                }
                ChatComposerCapabilityBar(
                    isClawMode = uiState.isClawMode,
                    isSaving = uiState.isSaving,
                    modelLabel = uiState.activeModelProfile?.model ?: "未配置模型",
                    reasoningEnabled = uiState.activeModelProfile?.reasoningEnabled == true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = 132.dp)
                            .testTag(AppTestTags.CHAT_INPUT),
                        minLines = 1,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (!uiState.isSaving && (draft.isNotBlank() || pendingAttachment != null)) {
                                    submitMessage()
                                }
                            }
                        ),
                        placeholder = {
                            Text(if (uiState.isClawMode) "输入 Claw 本地指令" else "向 lot 提问，或粘贴资料")
                        },
                        shape = RoundedCornerShape(LotRadius.lg),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            cursorColor = accent,
                            focusedLabelColor = accent
                        )
                    )
                    Box {
                        IconButton(
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(AppTestTags.CHAT_ATTACH_MENU)
                                .semantics { contentDescription = "添加附件" }
                                .clip(RoundedCornerShape(LotRadius.lg))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            onClick = { attachmentMenuExpanded = true }
                        ) {
                            ComposerAttachmentIcon(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = attachmentMenuExpanded,
                            onDismissRequest = { attachmentMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("拍照") },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    requestCamera()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("文档") },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    launchDocumentSelection()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("录音") },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    showRecordingPanel = true
                                }
                            )
                            if (uiState.messages.any { it.role == "user" }) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("重新生成上条") },
                                    enabled = !uiState.isSaving,
                                    onClick = {
                                        attachmentMenuExpanded = false
                                        viewModel.regenerateLastAssistant()
                                    }
                                )
                            }
                        }
                    }
                    if (uiState.isSaving && !uiState.isClawMode) {
                        Button(
                            onClick = viewModel::stopAssistantResponse,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = "停止生成" },
                            shape = RoundedCornerShape(LotRadius.lg),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            ComposerActionIcon(
                                action = ComposerAction.Stop,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    } else {
                        Button(
                            enabled = (draft.isNotBlank() || pendingAttachment != null),
                            onClick = ::submitMessage,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag(AppTestTags.CHAT_SEND)
                                .semantics {
                                    contentDescription = if (uiState.isClawMode) {
                                        "执行 Claw 指令"
                                    } else {
                                        "发送消息"
                                    }
                                },
                            shape = RoundedCornerShape(LotRadius.lg),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = if (uiState.isClawMode) {
                                    LotColors.OnClaw
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            )
                        ) {
                            ComposerActionIcon(
                                action = if (uiState.isClawMode) ComposerAction.Execute else ComposerAction.Send,
                                color = if (uiState.isClawMode) {
                                    LotColors.OnClaw
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PendingAttachment(
    val type: String,
    val json: String,
    val displayName: String,
    val sizeLabel: String,
    val mimeType: String?,
    val baseContent: String
)

private data class ClawSmsCommand(
    val userContent: String,
    val phoneNumber: String,
    val content: String
)

@Composable
private fun ChatComposerCapabilityBar(
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

private enum class ComposerAction {
    Send,
    Execute,
    Stop
}

@Composable
private fun ComposerAttachmentIcon(
    color: Color
) {
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
private fun ComposerActionIcon(
    action: ComposerAction,
    color: Color
) {
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
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun ThinkingIndicator(
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

private fun documentMimeTypes(): Array<String> {
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

@Composable
private fun RecordingControlCard(
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
                shape = RoundedCornerShape(8.dp)
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

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

@Composable
private fun ClawModeSwitch(
    enabled: Boolean,
    compact: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    val clawAccent = LotColors.Claw
    Row(
        modifier = Modifier
            .testTag(AppTestTags.CHAT_CLAW_SWITCH)
            .clip(RoundedCornerShape(LotRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onCheckedChange(!enabled) }
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeSegment(
            text = if (compact) "AI" else "AI 对话",
            selected = !enabled,
            selectedColor = MaterialTheme.colorScheme.primary
        )
        ModeSegment(
            text = if (compact) "Claw" else "Claw 本地",
            selected = enabled,
            selectedColor = clawAccent
        )
    }
}

@Composable
private fun ModeSegment(
    text: String,
    selected: Boolean,
    selectedColor: Color
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(LotRadius.md))
            .background(
                if (selected) selectedColor.copy(alpha = 0.16f) else Color.Transparent
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ModelQuickSwitcher(
    profiles: List<ModelProfile>,
    activeProfile: ModelProfile?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = activeProfile?.model ?: "切换模型",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)
        ) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = profile.model,
                            fontWeight = if (profile.id == activeProfile?.id) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSelect(profile.id)
                        expanded = false
                    }
                )
            }
            if (profiles.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("请先到设置页新增模型") },
                    enabled = false,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun ReasoningQuickToggle(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "思考",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AutoDismissMessageCard(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    var visible by remember(message) { mutableStateOf(true) }

    LaunchedEffect(message) {
        visible = true
        delay(1450)
        visible = false
        delay(LotMotion.slow.toLong())
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(LotMotion.fast)) +
            expandVertically(animationSpec = tween(LotMotion.fast)),
        exit = fadeOut(animationSpec = tween(LotMotion.slow)) +
            shrinkVertically(animationSpec = tween(LotMotion.slow))
    ) {
        AppSectionCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = message,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isError) FontWeight.Medium else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryDrawer(
    conversations: List<ConversationEntity>,
    clawTasks: List<AgentTaskEntity>,
    currentConversationId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onNewConversation: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var visibleCount by rememberSaveable { mutableIntStateOf(10) }
    var isLoadingMore by rememberSaveable { mutableStateOf(false) }
    var renameTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }
    var selectedTabName by rememberSaveable { mutableStateOf(HistoryDrawerTab.Conversations.name) }
    val selectedTab = runCatching { HistoryDrawerTab.valueOf(selectedTabName) }
        .getOrDefault(HistoryDrawerTab.Conversations)
    val renameTarget = conversations.firstOrNull { it.id == renameTargetId }
    val filteredConversations = conversations.filter { conversation ->
        val target = "${conversation.title} ${conversation.summary.orEmpty()}"
        target.contains(query, ignoreCase = true)
    }
    val visibleConversations = filteredConversations.take(visibleCount)
    val visibleConversationGroups = groupConversationsForHistory(visibleConversations)
    val filteredClawTasks = clawTasks.filter { task ->
        val target = "${task.type} ${task.status} ${task.description}"
        target.contains(query, ignoreCase = true)
    }
    val visibleClawTasks = filteredClawTasks.take(visibleCount)

    LaunchedEffect(query, conversations.size, clawTasks.size, selectedTabName) {
        visibleCount = 10
        isLoadingMore = false
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTargetId = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("会话名称") }
                )
            },
            confirmButton = {
                Button(
                    enabled = renameDraft.isNotBlank(),
                    onClick = {
                        onRename(renameTarget.id, renameDraft)
                        renameTargetId = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetId = null }) {
                    Text("取消")
                }
            }
        )
    }

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "历史记录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    enabled = selectedTab == HistoryDrawerTab.Conversations,
                    onClick = onNewConversation
                ) {
                    Text("新建")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == HistoryDrawerTab.Conversations,
                    onClick = { selectedTabName = HistoryDrawerTab.Conversations.name },
                    label = { Text("AI 会话 ${conversations.size}") }
                )
                FilterChip(
                    selected = selectedTab == HistoryDrawerTab.ClawLog,
                    onClick = { selectedTabName = HistoryDrawerTab.ClawLog.name },
                    label = { Text("Claw 日志 ${clawTasks.size}") }
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        if (selectedTab == HistoryDrawerTab.Conversations) {
                            "搜索会话"
                        } else {
                            "搜索命令、状态或结果"
                        }
                    )
                }
            )
            HorizontalDivider()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedTab) {
                    HistoryDrawerTab.Conversations -> {
                        if (filteredConversations.isEmpty()) {
                            item {
                                Text(
                                    text = "暂无会话",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            visibleConversationGroups.forEach { group ->
                                item(key = "history-group-${group.label}") {
                                    HistoryGroupHeader(
                                        label = group.label,
                                        count = group.conversations.size
                                    )
                                }
                                items(
                                    items = group.conversations,
                                    key = { it.id }
                                ) { conversation ->
                                    ConversationHistoryItem(
                                        conversation = conversation,
                                        selected = conversation.id == currentConversationId,
                                        onSelect = { onSelect(conversation.id) },
                                        onRename = {
                                            renameTargetId = conversation.id
                                            renameDraft = conversation.title
                                        },
                                        onDelete = { onDelete(conversation.id) }
                                    )
                                }
                            }
                            if (visibleConversations.size < filteredConversations.size) {
                                item(key = "load-more-history") {
                                    HistoryLoadMoreItem(
                                        visibleSize = visibleConversations.size,
                                        totalSize = filteredConversations.size,
                                        loadingText = "正在加载更早会话...",
                                        isLoadingMore = isLoadingMore,
                                        onLoadingChange = { isLoadingMore = it },
                                        onVisibleCountChange = { visibleCount = it }
                                    )
                                }
                            }
                        }
                    }
                    HistoryDrawerTab.ClawLog -> {
                        if (filteredClawTasks.isEmpty()) {
                            item {
                                Text(
                                    text = "暂无 Claw 命令日志",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(
                                items = visibleClawTasks,
                                key = { it.id }
                            ) { task ->
                                ClawTaskLogItem(task = task)
                            }
                            if (visibleClawTasks.size < filteredClawTasks.size) {
                                item(key = "load-more-claw-log") {
                                    HistoryLoadMoreItem(
                                        visibleSize = visibleClawTasks.size,
                                        totalSize = filteredClawTasks.size,
                                        loadingText = "正在加载更早命令...",
                                        isLoadingMore = isLoadingMore,
                                        onLoadingChange = { isLoadingMore = it },
                                        onVisibleCountChange = { visibleCount = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class HistoryDrawerTab {
    Conversations,
    ClawLog
}

private data class HistoryConversationGroup(
    val label: String,
    val conversations: List<ConversationEntity>
)

private fun groupConversationsForHistory(
    conversations: List<ConversationEntity>
): List<HistoryConversationGroup> {
    val grouped = conversations.groupBy { historyDateGroupLabel(it.updatedAt) }
    val order = listOf("今天", "昨天", "近 7 天", "更早")
    return order.mapNotNull { label ->
        grouped[label]?.takeIf { it.isNotEmpty() }?.let { items ->
            HistoryConversationGroup(label, items)
        }
    }
}

private fun historyDateGroupLabel(timestamp: Long): String {
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterdayStart = (todayStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val sevenDaysStart = (todayStart.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -6)
    }
    return when {
        target.timeInMillis >= todayStart.timeInMillis -> "今天"
        target.timeInMillis >= yesterdayStart.timeInMillis -> "昨天"
        target.timeInMillis >= sevenDaysStart.timeInMillis -> "近 7 天"
        else -> "更早"
    }
}

@Composable
private fun HistoryGroupHeader(
    label: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun HistoryLoadMoreItem(
    visibleSize: Int,
    totalSize: Int,
    loadingText: String,
    isLoadingMore: Boolean,
    onLoadingChange: (Boolean) -> Unit,
    onVisibleCountChange: (Int) -> Unit
) {
    LaunchedEffect(visibleSize, totalSize) {
        if (!isLoadingMore) {
            onLoadingChange(true)
            delay(450)
            onVisibleCountChange((visibleSize + 10).coerceAtMost(totalSize))
            onLoadingChange(false)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = loadingText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ClawTaskLogItem(task: AgentTaskEntity) {
    val statusTone = when (task.status.lowercase()) {
        "completed" -> StatusTone.Success
        "running" -> StatusTone.Primary
        else -> StatusTone.Neutral
    }
    val statusColor = when (task.status.lowercase()) {
        "completed" -> MaterialTheme.colorScheme.primary
        "running" -> MaterialTheme.colorScheme.tertiary
        "failed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, statusColor.copy(alpha = 0.28f), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    text = clawTaskTypeLabel(task.type),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTime(task.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                text = clawTaskStatusLabel(task.status),
                tone = statusTone
            )
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

private fun clawTaskTypeLabel(type: String): String {
    return when (type) {
        "sms" -> "短信命令"
        "camera" -> "拍照命令"
        "download" -> "下载命令"
        "document" -> "文档采集"
        "audio" -> "录音采集"
        "app-leap" -> "跨应用命令"
        "unknown" -> "未识别命令"
        else -> type
    }
}

private fun clawTaskStatusLabel(status: String): String {
    return when (status.lowercase()) {
        "completed" -> "成功"
        "running" -> "执行中"
        "failed" -> "失败"
        "cancelled" -> "已取消"
        else -> status
    }
}

@Composable
private fun ConversationHistoryItem(
    conversation: ConversationEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val shape = RoundedCornerShape(LotRadius.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, shape)
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(52.dp)
                .size(width = 3.dp, height = 52.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
                )
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = conversation.summary?.takeIf { it.isNotBlank() } ?: "本地会话",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${conversation.messageCount} 条消息 · ${formatTime(conversation.updatedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box {
            HistoryActionIconButton(
                action = HistoryAction.More,
                onClick = { menuExpanded = true }
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private enum class HistoryAction {
    Rename,
    Delete,
    More
}

@Composable
private fun HistoryActionIconButton(
    action: HistoryAction,
    onClick: () -> Unit
) {
    val description = when (action) {
        HistoryAction.Rename -> "重命名会话"
        HistoryAction.Delete -> "删除会话"
        HistoryAction.More -> "更多会话操作"
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
    ) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier = Modifier.size(17.dp)) {
            val stroke = Stroke(width = 1.8.dp.toPx())
            when (action) {
                HistoryAction.Rename -> {
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            size.width * 0.16f,
                            size.height * 0.24f
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            size.width * 0.48f,
                            size.height * 0.58f
                        ),
                        style = stroke
                    )
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.68f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.34f),
                        strokeWidth = stroke.width
                    )
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.28f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.86f, size.height * 0.40f),
                        strokeWidth = stroke.width
                    )
                }
                HistoryAction.Delete -> {
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.30f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.30f),
                        strokeWidth = stroke.width
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.36f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.46f),
                        style = stroke
                    )
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.22f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.22f),
                        strokeWidth = stroke.width
                    )
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.46f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.72f),
                        strokeWidth = stroke.width
                    )
                    drawLine(
                        color = color,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.46f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.72f),
                        strokeWidth = stroke.width
                    )
                }
                HistoryAction.More -> {
                    listOf(0.32f, 0.50f, 0.68f).forEach { x ->
                        drawCircle(
                            color = color,
                            radius = 1.8.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(size.width * x, size.height * 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatCard(
    onExampleSelected: (String) -> Unit
) {
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

@Composable
private fun MessageBubble(
    message: MessageEntity,
    modelLabel: String,
    reduceMotion: Boolean,
    onCopy: (String) -> Unit,
    onRetry: (String) -> Unit
) {
    val isUser = message.role == "user"
    val isClaw = message.role == "system"
    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isClaw -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.74f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val outlineColor = when {
        isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        isClaw -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    }
    val labelText = when {
        isUser -> "用户"
        isClaw -> "Claw 本地"
        message.isStreaming -> "AI 助手 · 思考中"
        else -> "AI 助手"
    }
    val labelColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        isClaw -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canRetry = !isUser &&
        !isClaw &&
        !message.isStreaming &&
        isRetryableAssistantError(message.content)
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
                    shape = RoundedCornerShape(8.dp)
                )
                .border(1.dp, outlineColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(min = 36.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            FileUtils.parseAttachmentJson(message.attachmentJson)?.let { attachment ->
                AttachmentCard(attachment)
            }
            if (message.isStreaming) {
                if (message.content.isBlank()) {
                    ThinkingIndicator(modelLabel = modelLabel, reduceMotion = reduceMotion)
                } else {
                    MessageContent(message.content)
                    ThinkingIndicator(modelLabel = modelLabel, reduceMotion = reduceMotion)
                }
            } else {
                MessageContent(message.content)
            }
            if (!message.isStreaming && message.content.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onCopy(message.content) }) {
                        Text("复制")
                    }
                    if (canRetry) {
                        TextButton(onClick = { onRetry(message.id) }) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

private fun isRetryableAssistantError(content: String): Boolean {
    return content.startsWith("API") ||
        content.startsWith("模型") ||
        content.contains("请求失败")
}

@Composable
private fun MessageContent(content: String) {
    val parts = content.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { index, part ->
            if (part.isBlank()) return@forEachIndexed
            if (index % 2 == 1) {
                CodeBlock(part)
            } else {
                Text(
                    text = part.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(rawCode: String) {
    val lines = rawCode.trim().lines()
    val firstLine = lines.firstOrNull().orEmpty()
    val code = if (firstLine.matches(Regex("[A-Za-z0-9_+.#-]{1,24}")) && lines.size > 1) {
        lines.drop(1).joinToString("\n")
    } else {
        rawCode.trim()
    }

    Text(
        text = code,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PendingAttachmentCard(
    attachment: PendingAttachment,
    onRemove: () -> Unit
) {
    val tint = attachmentTint(attachment.type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AppTestTags.CHAT_PENDING_ATTACHMENT)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = tint.copy(alpha = 0.32f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AttachmentTypeBadge(
                    text = pendingAttachmentLabel(attachment.type),
                    tint = tint
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "待发送附件",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = attachment.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${attachment.sizeLabel} · ${attachment.mimeType ?: "未知类型"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = onRemove) {
                Text("移除")
            }
        }
    }
}

@Composable
private fun AttachmentCard(attachment: AttachmentPreview) {
    val tint = attachmentTint(attachment.type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AttachmentTypeBadge(
                text = attachmentShortLabel(attachment.type),
                tint = tint
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = attachmentLabel(attachment.type),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${FileUtils.formatSize(attachment.sizeBytes)} · ${attachment.mimeType ?: "未知类型"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (attachment.type == "image") {
            AsyncImage(
                model = attachment.uri,
                contentDescription = attachment.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun attachmentTint(type: String): Color {
    return when (type) {
        "image" -> MaterialTheme.colorScheme.primary
        "audio" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
}

private fun attachmentLabel(type: String): String {
    return when (type) {
        "image" -> "图片附件"
        "audio" -> "录音附件"
        else -> "文档附件"
    }
}

@Composable
private fun AttachmentTypeBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = tint,
        fontWeight = FontWeight.SemiBold
    )
}

private fun pendingAttachmentLabel(type: String): String {
    return when (type) {
        "image" -> "图片"
        "audio" -> "录音"
        else -> "文档"
    }
}

private fun attachmentShortLabel(type: String): String {
    return when (type) {
        "image" -> "IMG"
        "audio" -> "AUD"
        else -> "DOC"
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun shouldShowNotificationPrompt(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
}
