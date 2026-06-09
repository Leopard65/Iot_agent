package com.example.iotgpt.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空历史会话") },
            text = { Text("这会删除所有本地会话、消息和模型统计记录，操作不可撤销。") },
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

    AppPage(
        title = "设置",
        subtitle = "模型服务、主题模式与调试操作",
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
        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "模型配置列表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (uiState.profiles.isEmpty()) {
                Text("暂无模型配置", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                uiState.profiles.forEach { profile ->
                    ModelProfileRow(
                        profile = profile,
                        selected = profile.id == uiState.activeProfileId,
                        onActivate = { viewModel.activateProfile(profile.id) },
                        onEdit = { viewModel.editProfile(profile.id) },
                        onDelete = { viewModel.deleteProfile(profile.id) }
                    )
                }
            }
            Button(onClick = viewModel::startNewProfile) {
                Text("新增模型配置")
            }
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "编辑模型配置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = uiState.profileName,
                onValueChange = viewModel::updateProfileName,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("配置名称") },
                placeholder = { Text("例如 DeepSeek 课堂演示") }
            )
            OutlinedTextField(
                value = uiState.provider,
                onValueChange = viewModel::updateProvider,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("服务商") },
                placeholder = { Text("DeepSeek / OpenAI / Custom") }
            )
            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Base URL") },
                placeholder = { Text("https://api.deepseek.com") }
            )
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::updateApiKey,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("API Key") },
                placeholder = { Text("输入后仅在本机保存") },
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = { showApiKey = !showApiKey }) {
                        Text(if (showApiKey) "隐藏" else "显示")
                    }
                }
            )
            OutlinedTextField(
                value = uiState.model,
                onValueChange = viewModel::updateModel,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("模型名称") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "支持图片输入",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "仅在所选 OpenAI 兼容服务支持 vision 时开启",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(
                    checked = uiState.supportsVision,
                    onCheckedChange = viewModel::updateSupportsVision
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !uiState.isSaving,
                    onClick = viewModel::saveSettings
                ) {
                    Text(if (uiState.isSaving) "保存中" else "保存并设为当前")
                }
                Button(
                    enabled = !uiState.isTesting,
                    onClick = viewModel::testConnection
                ) {
                    Text(if (uiState.isTesting) "测试中" else "测试连接")
                }
            }
            uiState.statusMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "预置模型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelPresetChip(
                        label = "DeepSeek",
                        selected = uiState.model == "deepseek-chat",
                        onClick = {
                            viewModel.applyPreset(
                                name = "DeepSeek",
                                provider = "DeepSeek",
                                baseUrl = "https://api.deepseek.com",
                                model = "deepseek-chat"
                            )
                        }
                    )
                    ModelPresetChip(
                        label = "通义",
                        selected = uiState.model.startsWith("qwen"),
                        onClick = {
                            viewModel.applyPreset(
                                name = "通义千问",
                                provider = "DashScope",
                                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode",
                                model = "qwen-plus"
                            )
                        }
                    )
                    ModelPresetChip(
                        label = "MiMo",
                        selected = uiState.model.contains("mimo", ignoreCase = true),
                        onClick = {
                            viewModel.applyPreset(
                                name = "小米 MiMo",
                                provider = "MiMo",
                                baseUrl = "https://api.xiaomimimo.com/v1",
                                model = "mimo-v2-pro"
                            )
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelPresetChip(
                        label = "OpenAI",
                        selected = uiState.baseUrl.contains("api.openai.com", ignoreCase = true),
                        onClick = {
                            viewModel.applyPreset(
                                name = "OpenAI",
                                provider = "OpenAI",
                                baseUrl = "https://api.openai.com/v1",
                                model = "gpt-4.1-mini"
                            )
                        }
                    )
                    ModelPresetChip(
                        label = "自定义",
                        selected = false,
                        onClick = {
                            viewModel.applyPreset(
                                name = "自定义模型",
                                provider = "Custom",
                                baseUrl = "",
                                model = ""
                            )
                        }
                    )
                }
            }
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "外观",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.updateThemeMode(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "维护",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::exportDebugInfo) {
                    Text("导出调试信息")
                }
                Button(
                    enabled = !uiState.isClearing,
                    onClick = { showClearConfirm = true }
                ) {
                    Text(if (uiState.isClearing) "清空中" else "清空历史会话")
                }
            }
            Text("IoTGPT 1.0 · AIoT Assistant")
        }

        uiState.debugInfo?.let { debugInfo ->
            AppSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "调试信息",
                    style = MaterialTheme.typography.titleMedium,
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
private fun ModelProfileRow(
    profile: ModelProfile,
    selected: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${profile.provider} · ${profile.model}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = profile.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                text = if (selected) "当前" else "可选",
                tone = if (selected) StatusTone.Success else StatusTone.Neutral
            )
        }
        Text(
            text = "${profile.maskedKeyStatus()} · 图片输入${if (profile.supportsVision) "已开启" else "未开启"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !selected,
                onClick = onActivate
            ) {
                Text("设为当前")
            }
            TextButton(onClick = onEdit) {
                Text("编辑")
            }
            TextButton(
                enabled = !selected,
                onClick = onDelete
            ) {
                Text("删除")
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
