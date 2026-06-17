package com.example.iotgpt.feature.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotgpt.core.components.AppPage
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone
import com.example.iotgpt.core.preferences.ModelProfile
import com.example.iotgpt.core.preferences.ThemeMode

/**
 * Settings screen for model profiles and app preferences.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showApiKey by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showEditorDialog by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空历史会话") },
            text = { Text("这会删除所有本地会话、消息、模型统计记录和 Claw 任务日志，操作不可撤销。") },
            confirmButton = {
                Button(
                    enabled = !uiState.isClearing,
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearHistory()
                    }
                ) {
                    Text(if (uiState.isClearing) "清空中" else "确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditorDialog) {
        ProfileEditorDialog(
            uiState = uiState,
            showApiKey = showApiKey,
            onShowApiKeyChange = { showApiKey = it },
            onDismiss = { showEditorDialog = false },
            onProfileNameChanged = viewModel::updateProfileName,
            onProviderChanged = viewModel::updateProvider,
            onBaseUrlChanged = viewModel::updateBaseUrl,
            onApiKeyChanged = viewModel::updateApiKey,
            onModelChanged = viewModel::updateModel,
            onSupportsVisionChanged = viewModel::updateSupportsVision,
            onSupportsReasoningChanged = viewModel::updateSupportsReasoning,
            onReasoningEnabledChanged = viewModel::updateReasoningEnabled,
            onSupportsAudioTranscriptionChanged = viewModel::updateSupportsAudioTranscription,
            onTranscriptionModelChanged = viewModel::updateTranscriptionModel,
            onApplyPreset = viewModel::applyPreset,
            onSave = viewModel::saveSettings,
            onTest = { viewModel.testConnection() }
        )
    }

    AppPage(
        title = "设置",
        subtitle = "",
        trailing = {
            StatusPill(
                text = if (uiState.activeProfile?.apiKey.isNullOrBlank()) {
                    "API 待配置"
                } else {
                    "API 已配置"
                },
                tone = StatusTone.Primary
            )
        }
    ) {
        SettingsStatusBanner(message = uiState.statusMessage)

        ActiveModelOverview(
            activeProfile = uiState.activeProfile,
            profileCount = uiState.profiles.size
        )

        AppSectionCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
            SectionHeader(
                title = "模型配置",
                subtitle = "保存多套配置；详细 URL、Key 和能力开关只在编辑页显示。",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                viewModel.duplicateActiveProfile()
                                showEditorDialog = true
                            }
                        ) {
                            Text("复制当前")
                        }
                        Button(
                            onClick = {
                                viewModel.startNewProfile()
                                showEditorDialog = true
                            }
                        ) {
                            Text("新增")
                        }
                    }
                }
            )
            if (uiState.profiles.isEmpty()) {
                Text("暂无模型配置", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                uiState.profiles.forEachIndexed { index, profile ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    ModelProfileRow(
                        profile = profile,
                        selected = profile.id == uiState.activeProfileId,
                        onActivate = { viewModel.activateProfile(profile.id) },
                        onEdit = {
                            viewModel.editProfile(profile.id)
                            showEditorDialog = true
                        },
                        onDelete = { viewModel.deleteProfile(profile.id) },
                        isTesting = uiState.testingProfileId == profile.id,
                        connectionResult = uiState.connectionResults[profile.id],
                        onTest = { viewModel.testConnection(profile.id) }
                    )
                }
            }
        }

        ThemePreferencePanel(
            selectedMode = uiState.themeMode,
            onSelect = viewModel::updateThemeMode
        )

        MaintenancePanel(
            isClearing = uiState.isClearing,
            onExportDebugInfo = viewModel::exportDebugInfo,
            onClearHistory = { showClearConfirm = true }
        )

        uiState.debugInfo?.let { debugInfo ->
            AppSectionCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(10.dp)
            ) {
                Text(
                    text = "调试信息",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = debugInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsStatusBanner(message: String?) {
    if (message.isNullOrBlank()) return
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ActiveModelOverview(
    activeProfile: ModelProfile?,
    profileCount: Int
) {
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
                    text = activeProfile?.name ?: "未选择模型",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = activeProfile?.model ?: "请新增或启用一个模型配置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(
                text = "$profileCount 套配置",
                tone = StatusTone.Neutral
            )
        }
        ModelCapabilityPills(profile = activeProfile)
        activeProfile?.baseUrl?.takeIf { it.isNotBlank() }?.let { baseUrl ->
            Text(
                text = baseUrl,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModelCapabilityPills(profile: ModelProfile?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(
                text = profile?.provider?.ifBlank { "Custom" } ?: "未配置",
                tone = StatusTone.Neutral
            )
        }
        if (profile != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (profile.supportsVision) {
                    StatusPill(text = "图片", tone = StatusTone.Primary)
                }
                if (profile.supportsReasoning) {
                    StatusPill(
                        text = if (profile.reasoningEnabled) "思考开" else "思考",
                        tone = if (profile.reasoningEnabled) StatusTone.Primary else StatusTone.Neutral
                    )
                }
                if (profile.supportsAudioTranscription) {
                    StatusPill(text = "转写", tone = StatusTone.Primary)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ThemePreferencePanel(
    selectedMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    AppSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        SectionHeader(
            title = "外观",
            subtitle = "即时切换浅色、深色，或跟随系统设置。"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
    }
}

@Composable
private fun MaintenancePanel(
    isClearing: Boolean,
    onExportDebugInfo: () -> Unit,
    onClearHistory: () -> Unit
) {
    AppSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        SectionHeader(
            title = "维护与隐私",
            subtitle = "导出调试摘要不会包含 API Key 明文；清空历史会删除本机会话和模型统计。"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExportDebugInfo) {
                Text("导出调试")
            }
            Button(
                enabled = !isClearing,
                onClick = onClearHistory
            ) {
                Text(if (isClearing) "清空中" else "清空历史")
            }
        }
        Text(
            text = "lot 1.0 · IoT AI Assistant",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileEditorDialog(
    uiState: SettingsUiState,
    showApiKey: Boolean,
    onShowApiKeyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onProfileNameChanged: (String) -> Unit,
    onProviderChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onSupportsVisionChanged: (Boolean) -> Unit,
    onSupportsReasoningChanged: (Boolean) -> Unit,
    onReasoningEnabledChanged: (Boolean) -> Unit,
    onSupportsAudioTranscriptionChanged: (Boolean) -> Unit,
    onTranscriptionModelChanged: (String) -> Unit,
    onApplyPreset: (String, String, String, String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (uiState.editingProfileId == null) "新增模型配置" else "编辑模型配置",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelPresetSection(uiState = uiState, onApplyPreset = onApplyPreset)
                OutlinedTextField(
                    value = uiState.profileName,
                    onValueChange = onProfileNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("配置名称") },
                    placeholder = { Text("例如 DeepSeek 日常助手") }
                )
                OutlinedTextField(
                    value = uiState.provider,
                    onValueChange = onProviderChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("服务商") }
                )
                OutlinedTextField(
                    value = uiState.baseUrl,
                    onValueChange = onBaseUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.deepseek.com") }
                )
                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = onApiKeyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("API Key") },
                    placeholder = { Text("输入后仅在本机保存") },
                    visualTransformation = if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { onShowApiKeyChange(!showApiKey) }) {
                            Text(if (showApiKey) "隐藏" else "显示")
                        }
                    }
                )
                OutlinedTextField(
                    value = uiState.model,
                    onValueChange = onModelChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text("模型名称") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "支持图片输入",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "仅在服务支持 vision 时开启",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = uiState.supportsVision,
                        onCheckedChange = onSupportsVisionChanged
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "支持思考模式",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "开启后请求体会携带真实思考参数",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = uiState.supportsReasoning,
                        onCheckedChange = onSupportsReasoningChanged
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "默认开启思考",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.supportsReasoning) {
                                "聊天页也可以临时切换"
                            } else {
                                "先声明模型支持思考模式"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        enabled = uiState.supportsReasoning,
                        checked = uiState.supportsReasoning && uiState.reasoningEnabled,
                        onCheckedChange = onReasoningEnabledChanged
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "支持语音转写",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "开启后录音附件会先转文字再进入对话上下文",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Checkbox(
                        checked = uiState.supportsAudioTranscription,
                        onCheckedChange = onSupportsAudioTranscriptionChanged
                    )
                }
                if (uiState.supportsAudioTranscription) {
                    OutlinedTextField(
                        value = uiState.transcriptionModel,
                        onValueChange = onTranscriptionModelChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        label = { Text("语音转写模型") },
                        placeholder = { Text("mimo-v2.5-asr 或 whisper-1") }
                    )
                }
                uiState.statusMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !uiState.isSaving,
                onClick = onSave
            ) {
                Text(if (uiState.isSaving) "保存中" else "保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    enabled = !uiState.isTesting,
                    onClick = onTest
                ) {
                    Text(if (uiState.isTesting) "测试中" else "测试连接")
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

@Composable
private fun ModelPresetSection(
    uiState: SettingsUiState,
    onApplyPreset: (String, String, String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "预置模型",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModelPresetChip(
                label = "DeepSeek",
                selected = uiState.model == "deepseek-chat",
                onClick = {
                    onApplyPreset(
                        "DeepSeek",
                        "DeepSeek",
                        "https://api.deepseek.com",
                        "deepseek-chat"
                    )
                }
            )
            ModelPresetChip(
                label = "通义",
                selected = uiState.model.startsWith("qwen"),
                onClick = {
                    onApplyPreset(
                        "通义千问",
                        "DashScope",
                        "https://dashscope.aliyuncs.com/compatible-mode",
                        "qwen-plus"
                    )
                }
            )
            ModelPresetChip(
                label = "MiMo",
                selected = uiState.model.contains("mimo", ignoreCase = true),
                onClick = {
                    onApplyPreset(
                        "小米 MiMo",
                        "MiMo",
                        "https://api.xiaomimimo.com/v1",
                        "mimo-v2.5"
                    )
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModelPresetChip(
                label = "OpenAI",
                selected = uiState.baseUrl.contains("api.openai.com", ignoreCase = true),
                onClick = {
                    onApplyPreset(
                        "OpenAI",
                        "OpenAI",
                        "https://api.openai.com/v1",
                        "gpt-4.1-mini"
                    )
                }
            )
            ModelPresetChip(
                label = "自定义",
                selected = false,
                onClick = {
                    onApplyPreset(
                        "自定义模型",
                        "Custom",
                        "",
                        ""
                    )
                }
            )
        }
    }
}

@Composable
private fun ModelProfileRow(
    profile: ModelProfile,
    selected: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    isTesting: Boolean,
    connectionResult: String?,
    onTest: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .border(1.dp, borderColor, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = profile.model,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusPill(
                    text = if (selected) "当前" else "可选",
                    tone = if (selected) StatusTone.Success else StatusTone.Neutral
                )
                connectionResult?.let { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            result.contains("成功") -> MaterialTheme.colorScheme.primary
                            result.contains("测试中") -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(
            text = profile.baseUrl.ifBlank { "Base URL 未配置" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ModelCapabilityPills(profile = profile)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                enabled = !selected,
                onClick = onActivate,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                ProfileActionIcon(
                    action = ProfileAction.Activate,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "启用",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            ProfileActionIconButton(
                action = ProfileAction.Edit,
                enabled = true,
                onClick = onEdit
            )
            ProfileActionIconButton(
                action = ProfileAction.Test,
                enabled = !isTesting,
                onClick = onTest
            )
            ProfileActionIconButton(
                action = ProfileAction.Delete,
                enabled = !selected,
                onClick = onDelete
            )
        }
    }
}

private enum class ProfileAction(
    val contentDescription: String
) {
    Activate("启用模型配置"),
    Edit("编辑模型配置"),
    Test("测试连接"),
    Delete("删除模型配置")
}

@Composable
private fun ProfileActionIconButton(
    action: ProfileAction,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = action.contentDescription }
    ) {
        ProfileActionIcon(
            action = action,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            }
        )
    }
}

@Composable
private fun ProfileActionIcon(
    action: ProfileAction,
    color: Color
) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        when (action) {
            ProfileAction.Activate -> {
                val path = Path().apply {
                    moveTo(size.width * 0.34f, size.height * 0.24f)
                    lineTo(size.width * 0.34f, size.height * 0.76f)
                    lineTo(size.width * 0.76f, size.height * 0.50f)
                    close()
                }
                drawPath(path = path, color = color, style = stroke)
            }
            ProfileAction.Edit -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.24f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.48f, size.height * 0.58f),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.50f, size.height * 0.66f),
                    end = Offset(size.width * 0.82f, size.height * 0.34f),
                    strokeWidth = stroke.width
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.75f, size.height * 0.28f),
                    end = Offset(size.width * 0.86f, size.height * 0.39f),
                    strokeWidth = stroke.width
                )
            }
            ProfileAction.Test -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.32f,
                    center = Offset(size.width * 0.50f, size.height * 0.50f),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.50f, size.height * 0.18f),
                    end = Offset(size.width * 0.50f, size.height * 0.04f),
                    strokeWidth = stroke.width
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.50f, size.height * 0.82f),
                    end = Offset(size.width * 0.50f, size.height * 0.96f),
                    strokeWidth = stroke.width
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.50f),
                    end = Offset(size.width * 0.04f, size.height * 0.50f),
                    strokeWidth = stroke.width
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.82f, size.height * 0.50f),
                    end = Offset(size.width * 0.96f, size.height * 0.50f),
                    strokeWidth = stroke.width
                )
            }
            ProfileAction.Delete -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.22f, size.height * 0.30f),
                    end = Offset(size.width * 0.78f, size.height * 0.30f),
                    strokeWidth = stroke.width
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.36f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.46f),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.40f, size.height * 0.22f),
                    end = Offset(size.width * 0.60f, size.height * 0.22f),
                    strokeWidth = stroke.width
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.42f, size.height * 0.46f),
                    end = Offset(size.width * 0.42f, size.height * 0.72f),
                    strokeWidth = stroke.width
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.58f, size.height * 0.46f),
                    end = Offset(size.width * 0.58f, size.height * 0.72f),
                    strokeWidth = stroke.width
                )
            }
        }
    }
}

@Composable
private fun ModelPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
