package com.example.iotgpt.feature.settings.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotgpt.core.database.AppDatabase
import com.example.iotgpt.core.network.LlmApiService
import com.example.iotgpt.core.network.LlmChatMessage
import com.example.iotgpt.core.network.OpenAiCompatibleClient
import com.example.iotgpt.core.preferences.LlmSettings
import com.example.iotgpt.core.preferences.ModelProfile
import com.example.iotgpt.core.preferences.SettingsStore
import com.example.iotgpt.core.preferences.ThemeMode
import java.util.UUID
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages editable model API settings stored in DataStore.
 */
class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val database = AppDatabase.getInstance(application)
    private val llmApiService: LlmApiService = OpenAiCompatibleClient()
    private val appContext = application

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                settingsStore.modelProfiles,
                settingsStore.activeModelProfile,
                settingsStore.themeMode
            ) { profiles, activeProfile, themeMode ->
                Triple(profiles, activeProfile, themeMode)
            }.collect { (profiles, activeProfile, themeMode) ->
                _uiState.update { state ->
                    val activeChanged = state.activeProfileId != activeProfile.id
                    val editingProfileMissing = state.editingProfileId != null &&
                        profiles.none { it.id == state.editingProfileId }
                    val shouldRefreshEditor = state.editingProfileId == null ||
                        state.editingProfileId == activeProfile.id ||
                        activeChanged ||
                        editingProfileMissing
                    state.copy(
                        profiles = profiles,
                        activeProfileId = activeProfile.id,
                        themeMode = themeMode,
                        editingProfileId = if (shouldRefreshEditor) activeProfile.id else state.editingProfileId,
                        profileName = if (shouldRefreshEditor) activeProfile.name else state.profileName,
                        provider = if (shouldRefreshEditor) activeProfile.provider else state.provider,
                        baseUrl = if (shouldRefreshEditor) activeProfile.baseUrl else state.baseUrl,
                        apiKey = if (shouldRefreshEditor) activeProfile.apiKey else state.apiKey,
                        model = if (shouldRefreshEditor) activeProfile.model else state.model,
                        supportsVision = if (shouldRefreshEditor) {
                            activeProfile.supportsVision
                        } else {
                            state.supportsVision
                        }
                    )
                }
            }
        }
    }

    fun updateProfileName(value: String) {
        _uiState.update { it.copy(profileName = value, statusMessage = null) }
    }

    fun updateProvider(value: String) {
        _uiState.update { it.copy(provider = value, statusMessage = null) }
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value, statusMessage = null) }
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value, statusMessage = null) }
    }

    fun updateModel(value: String) {
        _uiState.update { it.copy(model = value, statusMessage = null) }
    }

    fun updateSupportsVision(value: Boolean) {
        _uiState.update { it.copy(supportsVision = value, statusMessage = null) }
    }

    fun applyPreset(
        name: String,
        provider: String,
        baseUrl: String,
        model: String
    ) {
        _uiState.update {
            it.copy(
                profileName = name,
                provider = provider,
                baseUrl = baseUrl,
                model = model,
                supportsVision = false,
                statusMessage = null
            )
        }
    }

    fun editProfile(profileId: String) {
        val profile = _uiState.value.profiles.firstOrNull { it.id == profileId } ?: return
        _uiState.update {
            it.copy(
                editingProfileId = profile.id,
                profileName = profile.name,
                provider = profile.provider,
                baseUrl = profile.baseUrl,
                apiKey = profile.apiKey,
                model = profile.model,
                supportsVision = profile.supportsVision,
                statusMessage = "正在编辑 ${profile.name}"
            )
        }
    }

    fun startNewProfile() {
        _uiState.update {
            it.copy(
                editingProfileId = null,
                profileName = "自定义模型",
                provider = "Custom",
                baseUrl = "",
                apiKey = "",
                model = "",
                supportsVision = false,
                statusMessage = "正在新建模型配置"
            )
        }
    }

    fun activateProfile(profileId: String) {
        viewModelScope.launch {
            runCatching {
                settingsStore.setActiveModelProfile(profileId)
            }.onSuccess {
                val profile = _uiState.value.profiles.firstOrNull { it.id == profileId }
                _uiState.update {
                    it.copy(statusMessage = "当前聊天模型已切换为 ${profile?.name ?: "所选配置"}")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(statusMessage = error.message ?: "切换模型配置失败") }
            }
        }
    }

    fun deleteProfile(profileId: String) {
        val state = _uiState.value
        if (profileId == state.activeProfileId) {
            _uiState.update { it.copy(statusMessage = "当前配置不能删除，请先切换到其他配置") }
            return
        }
        viewModelScope.launch {
            runCatching {
                settingsStore.deleteModelProfile(profileId)
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        editingProfileId = current.activeProfileId,
                        statusMessage = "模型配置已删除"
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(statusMessage = error.message ?: "删除模型配置失败") }
            }
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsStore.saveThemeMode(themeMode)
            _uiState.update {
                it.copy(
                    themeMode = themeMode,
                    statusMessage = "主题已切换为${themeMode.label}"
                )
            }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            runCatching {
                val state = _uiState.value
                require(state.profileName.isNotBlank()) { "请填写配置名称" }
                require(state.baseUrl.isNotBlank()) { "请填写 Base URL" }
                require(state.model.isNotBlank()) { "请填写模型名称" }
                val profile = ModelProfile(
                    id = state.editingProfileId ?: "profile-${UUID.randomUUID()}",
                    name = state.profileName,
                    provider = state.provider,
                    baseUrl = state.baseUrl,
                    apiKey = state.apiKey,
                    model = state.model,
                    supportsVision = state.supportsVision
                )
                settingsStore.upsertModelProfile(profile, activate = true)
            }.onSuccess {
                _uiState.update {
                    it.copy(isSaving = false, statusMessage = "模型配置已保存并设为当前")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = error.message ?: "保存 API 配置失败"
                    )
                }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, statusMessage = null) }
            val settings = _uiState.value.toLlmSettings()
            val result = runCatching {
                llmApiService.createChatCompletion(
                    settings = settings,
                    messages = listOf(
                        LlmChatMessage(
                            role = "user",
                            content = "请用一句话回复：AIoT Assistant 连接成功。"
                        )
                    ),
                    temperature = 0.2
                )
            }
            result.onSuccess {
                settingsStore.saveLastApiError(null)
                _uiState.update {
                    it.copy(isTesting = false, statusMessage = "API 连接成功")
                }
            }.onFailure { error ->
                val message = error.message ?: "API 连接失败"
                settingsStore.saveLastApiError(message)
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        statusMessage = message
                    )
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearing = true, statusMessage = null) }
            runCatching {
                database.conversationDao().clearConversations()
                database.modelUsageDao().clearUsage()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        statusMessage = "历史会话和模型统计已清空"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isClearing = false,
                        statusMessage = error.message ?: "清空历史失败"
                    )
                }
            }
        }
    }

    fun exportDebugInfo() {
        viewModelScope.launch {
            runCatching {
                val conversationCount = database.conversationDao().observeConversationCount().first()
                val messageCount = database.messageDao().observeMessageCount().first()
                val usage = database.modelUsageDao().observeUsageByModel().first()
                val profiles = _uiState.value.profiles
                val lastApiError = settingsStore.lastApiError.first()
                buildString {
                    appendLine("IoTGPT Debug Info")
                    appendLine("Version: ${readVersionName()}")
                    appendLine("Active profile: ${_uiState.value.activeProfile?.name.orEmpty()}")
                    appendLine("Active model: ${_uiState.value.activeProfile?.model.orEmpty()}")
                    appendLine("Profiles: ${profiles.size}")
                    profiles.forEach { profile ->
                        appendLine(
                            "- ${profile.name} / ${profile.provider} / ${profile.model} / " +
                                "vision=${profile.supportsVision} / key=${profile.apiKey.isNotBlank()}"
                        )
                    }
                    appendLine("Theme: ${_uiState.value.themeMode.label}")
                    appendLine("Last API error: ${lastApiError ?: "none"}")
                    appendLine("Conversations: $conversationCount")
                    appendLine("Messages: $messageCount")
                    appendLine("Model calls: ${usage.sumOf { it.callCount }}")
                    appendLine("Token/char estimate: ${usage.sumOf { it.totalTokens }}")
                }
            }.onSuccess { debugInfo ->
                _uiState.update {
                    it.copy(
                        debugInfo = debugInfo,
                        statusMessage = "调试信息已生成"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "导出调试信息失败")
                }
            }
        }
    }

    private fun SettingsUiState.toLlmSettings(): LlmSettings {
        return LlmSettings(
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            supportsVision = supportsVision
        )
    }

    private fun readVersionName(): String {
        return runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
                .orEmpty()
        }.getOrDefault("1.0")
    }
}

data class SettingsUiState(
    val profiles: List<ModelProfile> = emptyList(),
    val activeProfileId: String? = null,
    val editingProfileId: String? = null,
    val profileName: String = "DeepSeek",
    val provider: String = "DeepSeek",
    val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = SettingsStore.DEFAULT_MODEL,
    val supportsVision: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val isClearing: Boolean = false,
    val debugInfo: String? = null,
    val statusMessage: String? = null
) {
    val activeProfile: ModelProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }
}
