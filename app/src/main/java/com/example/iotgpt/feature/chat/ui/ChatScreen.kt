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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.core.preferences.ModelProfile
import com.example.iotgpt.core.util.AttachmentPreview
import com.example.iotgpt.core.util.AudioRecorder
import com.example.iotgpt.core.util.FileUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Room-backed chat screen with local conversation history.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val audioRecorder = remember { AudioRecorder(context) }
    val messageListState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachment by remember { mutableStateOf<PendingAttachment?>(null) }
    var attachmentMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var visibleMessageCount by rememberSaveable(uiState.currentConversationId) {
        mutableIntStateOf(10)
    }
    var isLoadingOlderMessages by rememberSaveable(uiState.currentConversationId) {
        mutableStateOf(false)
    }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var notificationPromptDismissed by rememberSaveable { mutableStateOf(false) }
    val visibleMessages = uiState.messages.takeLast(visibleMessageCount)

    DisposableEffect(Unit) {
        onDispose { audioRecorder.cancel() }
    }

    fun prepareAttachment(type: String, uri: Uri) {
        if (FileUtils.isTooLarge(context, uri)) {
            viewModel.showError("文件超过 10MB，暂不支持上传")
            return
        }
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
        val info = FileUtils.queryAttachmentInfo(context, uri)
        val typeLabel = when (type) {
            "image" -> "图片"
            "audio" -> "录音"
            else -> "文档"
        }
        val previewText = textPreview?.let {
            "\n\n可读取的文本内容：\n$it"
        }.orEmpty()
        val sizeLabel = FileUtils.formatSize(info.sizeBytes)
        val mimeType = info.mimeType ?: "未知"
        val content = "用户上传$typeLabel：${info.displayName}，大小 $sizeLabel，类型 $mimeType。$previewText"
        pendingAttachment = PendingAttachment(
            type = type,
            json = json,
            displayName = info.displayName,
            sizeLabel = sizeLabel,
            mimeType = info.mimeType,
            baseContent = content,
            textPreview = textPreview
        )
        viewModel.showNotice("$typeLabel 已添加，可直接发送，也可以继续输入说明后一起发送")
    }

    fun submitMessage() {
        val messageText = draft.trim()
        val attachment = pendingAttachment
        when {
            attachment != null -> {
                val content = if (messageText.isBlank()) {
                    attachment.baseContent
                } else {
                    "$messageText\n\n${attachment.baseContent}"
                }
                viewModel.sendAttachment(content, attachment.json)
                pendingAttachment = null
                draft = ""
            }
            messageText.isNotBlank() -> {
                viewModel.sendMessage(messageText)
                draft = ""
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            prepareAttachment("image", uri)
        } else {
            viewModel.showError("拍照已取消或失败")
        }
        pendingCameraUri = null
    }

    fun launchCameraCapture() {
        val uri = FileUtils.createImageUri(context)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            viewModel.showError("相机权限被拒绝，无法拍照")
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            viewModel.showError("未选择文档")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        prepareAttachment("document", uri)
    }

    fun beginRecording() {
        runCatching {
            audioRecorder.start()
            isRecording = true
        }.onFailure {
            viewModel.showError("录音启动失败：${it.message ?: "请检查麦克风权限"}")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginRecording()
        } else {
            viewModel.showError("录音权限被拒绝，无法采集音频")
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

    fun toggleRecording() {
        if (isRecording) {
            val uri = audioRecorder.stop()
            isRecording = false
            if (uri != null) {
                prepareAttachment("audio", uri)
            } else {
                viewModel.showError("录音保存失败")
            }
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginRecording()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistoryDrawer(
                conversations = uiState.conversations,
                currentConversationId = uiState.currentConversationId,
                onSelect = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onDelete = viewModel::deleteConversation,
                onNewConversation = {
                    viewModel.createConversation()
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        AppPage(
            title = uiState.currentConversation?.title ?: "AIoT 对话",
            subtitle = "",
            scrollable = false,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { scope.launch { drawerState.open() } }) {
                    Text("历史")
                }
                ModelQuickSwitcher(
                    profiles = uiState.modelProfiles,
                    activeProfile = uiState.activeModelProfile,
                    onSelect = viewModel::activateModelProfile,
                    onModelChanged = viewModel::updateActiveModelName,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = viewModel::createConversation) {
                    Text("新建")
                }
            }

            uiState.errorMessage?.let { error ->
                AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = viewModel::dismissError) {
                        Text("知道了")
                    }
                }
            }

            uiState.noticeMessage?.let { notice ->
                AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = notice,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = viewModel::dismissNotice) {
                        Text("关闭")
                    }
                }
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
                    .fillMaxWidth(),
                state = messageListState,
                reverseLayout = uiState.messages.isNotEmpty(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        MessageBubble(
                            message = message,
                            onCopy = { text ->
                                clipboardManager.setPrimaryClip(
                                    ClipData.newPlainText("AIoT message", text)
                                )
                                viewModel.showNotice("消息已复制")
                            },
                            onRetry = viewModel::retryAssistantMessage
                        )
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

            AppSectionCard(contentPadding = PaddingValues(10.dp)) {
                pendingAttachment?.let { attachment ->
                    PendingAttachmentCard(
                        attachment = attachment,
                        onRemove = {
                            pendingAttachment = null
                            viewModel.showNotice("附件已移除")
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (!uiState.isSaving && (draft.isNotBlank() || pendingAttachment != null)) {
                                    submitMessage()
                                }
                            }
                        ),
                        placeholder = { Text("输入消息") }
                    )
                    Box {
                        TextButton(onClick = { attachmentMenuExpanded = true }) {
                            Text("+")
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
                                    documentLauncher.launch(
                                        arrayOf(
                                            "text/plain",
                                            "application/pdf",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "*/*"
                                        )
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isRecording) "停止录音" else "录音") },
                                onClick = {
                                    attachmentMenuExpanded = false
                                    toggleRecording()
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
                    Button(
                        enabled = (draft.isNotBlank() || pendingAttachment != null) && !uiState.isSaving,
                        onClick = ::submitMessage
                    ) {
                        Text(if (uiState.isSaving) "请求中" else "发送")
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
    val baseContent: String,
    val textPreview: String?
)

@Composable
private fun ModelQuickSwitcher(
    profiles: List<ModelProfile>,
    activeProfile: ModelProfile?,
    onSelect: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var modelDraft by rememberSaveable(activeProfile?.id) {
        mutableStateOf(activeProfile?.model.orEmpty())
    }
    val suggestions = remember(activeProfile?.provider, activeProfile?.baseUrl) {
        modelSuggestions(activeProfile)
    }

    LaunchedEffect(activeProfile?.id, activeProfile?.model) {
        modelDraft = activeProfile?.model.orEmpty()
    }

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
            modifier = Modifier.widthIn(min = 300.dp, max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = activeProfile?.name ?: "当前模型",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = activeProfile
                        ?.let {
                            "${it.provider} · ${it.model} · ${it.maskedKeyStatus()} · " +
                                "图片输入${if (it.supportsVision) "已开启" else "未开启"}"
                        }
                        ?: "请先在设置页配置模型",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider()
            Text(
                text = "模型配置",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = profile.name,
                                fontWeight = if (profile.id == activeProfile?.id) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                            Text(
                                text = "${profile.provider} · ${profile.model}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    onClick = {
                        onSelect(profile.id)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            Text(
                text = "同服务商模型",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            suggestions.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        modelDraft = model
                        onModelChanged(model)
                        expanded = false
                    }
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("模型名称") }
                )
                Button(
                    enabled = modelDraft.isNotBlank(),
                    onClick = {
                        onModelChanged(modelDraft)
                        expanded = false
                    }
                ) {
                    Text("应用")
                }
            }
        }
    }
}

private fun modelSuggestions(profile: ModelProfile?): List<String> {
    if (profile == null) return emptyList()
    val marker = "${profile.provider} ${profile.baseUrl}".lowercase()
    val defaults = when {
        marker.contains("deepseek") -> listOf("deepseek-chat", "deepseek-reasoner")
        marker.contains("dashscope") || marker.contains("qwen") -> listOf(
            "qwen-plus",
            "qwen-turbo",
            "qwen-max",
            "qwen-vl-plus"
        )
        marker.contains("openai") -> listOf("gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini", "gpt-4o")
        marker.contains("mimo") -> listOf("mimo-v2-pro")
        else -> emptyList()
    }
    return (listOf(profile.model) + defaults)
        .filter { it.isNotBlank() }
        .distinct()
}

@Composable
private fun HistoryDrawer(
    conversations: List<ConversationEntity>,
    currentConversationId: String?,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNewConversation: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var visibleCount by rememberSaveable { mutableIntStateOf(10) }
    var isLoadingMore by rememberSaveable { mutableStateOf(false) }
    val filteredConversations = conversations.filter { conversation ->
        val target = "${conversation.title} ${conversation.summary.orEmpty()}"
        target.contains(query, ignoreCase = true)
    }
    val visibleConversations = filteredConversations.take(visibleCount)

    LaunchedEffect(query, conversations.size) {
        visibleCount = 10
        isLoadingMore = false
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
                    text = "历史会话",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Button(onClick = onNewConversation) {
                    Text("新建")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索会话") }
            )
            HorizontalDivider()
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredConversations.isEmpty()) {
                    item {
                        Text(
                            text = "暂无会话",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(
                        items = visibleConversations,
                        key = { it.id }
                    ) { conversation ->
                        ConversationHistoryItem(
                            conversation = conversation,
                            selected = conversation.id == currentConversationId,
                            onSelect = { onSelect(conversation.id) },
                            onDelete = { onDelete(conversation.id) }
                        )
                    }
                    if (visibleConversations.size < filteredConversations.size) {
                        item(key = "load-more-history") {
                            LaunchedEffect(visibleConversations.size, filteredConversations.size) {
                                if (!isLoadingMore) {
                                    isLoadingMore = true
                                    delay(450)
                                    visibleCount = (visibleCount + 10)
                                        .coerceAtMost(filteredConversations.size)
                                    isLoadingMore = false
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
                                    text = "正在加载更早会话...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationHistoryItem(
    conversation: ConversationEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = conversation.summary ?: "本地会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onDelete) {
                Text("删除")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${conversation.messageCount} 条消息",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatTime(conversation.updatedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyChatCard(
    onExampleSelected: (String) -> Unit
) {
    val examples = listOf(
        "请解释 MQTT QoS 0、1、2 的区别，并给一个传感器上报场景。",
        "ESP32 通过 HTTP 上传温湿度数据时，如何设计重试机制？",
        "Modbus RTU 和 Modbus TCP 在物联网网关中分别适合什么场景？"
    )
    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "开始一次 AIoT 会话",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "可以记录传感器、MQTT、嵌入式开发或设备联网问题，消息会保存到本地历史。",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        examples.forEach { example ->
            TextButton(onClick = { onExampleSelected(example) }) {
                Text(example)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    onCopy: (String) -> Unit,
    onRetry: (String) -> Unit
) {
    val isUser = message.role == "user"
    val canRetry = !isUser &&
        !message.isStreaming &&
        isRetryableAssistantError(message.content)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .widthIn(max = 560.dp)
                .background(
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
                .heightIn(min = 36.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FileUtils.parseAttachmentJson(message.attachmentJson)?.let { attachment ->
                AttachmentCard(attachment)
            }
            Text(
                text = when {
                    isUser -> "用户"
                    message.isStreaming -> "AI 助手 · 思考中"
                    else -> "AI 助手"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            if (message.isStreaming) {
                Text(
                    text = "正在请求模型回复...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                MessageContent(message.content)
            }
            if (message.isStreaming) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!message.isStreaming && message.content.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (attachment.type) {
                        "image" -> "待发送图片"
                        "audio" -> "待发送录音"
                        else -> "待发送文档"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${attachment.sizeLabel} · ${attachment.mimeType ?: "未知类型"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            TextButton(onClick = onRemove) {
                Text("移除")
            }
        }
        attachment.textPreview?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it.take(180),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttachmentCard(attachment: AttachmentPreview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = when (attachment.type) {
                "image" -> "图片附件"
                "audio" -> "录音附件"
                else -> "文档附件"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = attachment.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${FileUtils.formatSize(attachment.sizeBytes)} · ${attachment.mimeType ?: "未知类型"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        attachment.textPreview?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it.take(160),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
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
