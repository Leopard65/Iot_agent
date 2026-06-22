package com.example.iotgpt.feature.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotgpt.core.database.AppDatabase
import com.example.iotgpt.core.database.entity.AgentTaskEntity
import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.core.notification.NotificationHelper
import com.example.iotgpt.core.preferences.ModelProfile
import com.example.iotgpt.core.preferences.SettingsStore
import com.example.iotgpt.core.util.FileUtils
import com.example.iotgpt.feature.chat.ChatRepository
import com.example.iotgpt.feature.chat.ChatRepositoryImpl
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coordinates local chat history, message input and active conversation state.
 */
class ChatViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val database = AppDatabase.getInstance(application)
    private val agentTaskDao = database.agentTaskDao()
    private val repository: ChatRepository = ChatRepositoryImpl(
        database = database,
        settingsStore = settingsStore,
        appContext = application,
        notificationHelper = NotificationHelper(application)
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState
    private var responseJob: Job? = null

    init {
        observeConversations()
        observeCurrentMessages()
        observeModelProfiles()
        observeClawCommandLogs()
        observePagination()
    }

    fun createConversation() {
        viewModelScope.launch {
            runCatching { repository.createConversation() }
                .onSuccess { conversation ->
                    _uiState.update {
                        it.copy(
                            currentConversationId = conversation.id,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.readableMessage()) }
                }
        }
    }

    fun selectConversation(id: String) {
        _uiState.update {
            it.copy(
                currentConversationId = id,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            repository.resetMessagePagination()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteConversation(id) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            currentConversationId = state.currentConversationId.takeUnless { it == id },
                            errorMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.readableMessage()) }
                }
        }
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch {
            runCatching { repository.renameConversation(id, title) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            errorMessage = null,
                            noticeMessage = "会话已重命名"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.readableMessage()) }
                }
        }
    }

    fun exportCurrentConversation() {
        val conversationId = _uiState.value.currentConversationId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null) }
            runCatching {
                repository.exportConversationMarkdown(conversationId)
            }.onSuccess { export ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        pendingExport = ChatExportUiState(
                            fileName = export.fileName,
                            markdown = export.markdown
                        ),
                        noticeMessage = "对话导出已生成"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        errorMessage = error.readableMessage()
                    )
                }
            }
        }
    }

    fun consumePendingExport() {
        _uiState.update { it.copy(pendingExport = null) }
    }

    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                repository.sendUserMessage(
                    conversationId = _uiState.value.currentConversationId,
                    content = trimmed
                )
            }.onSuccess { conversationId ->
                _uiState.update {
                    it.copy(
                        currentConversationId = conversationId,
                        isSaving = false,
                        noticeMessage = "AI 回复已保存"
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.readableMessage()
                    )
                }
            }
        }
    }

    fun sendAttachment(content: String, attachmentJson: String) {
        val trimmed = content.trim()
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                repository.sendAttachmentMessage(
                    conversationId = _uiState.value.currentConversationId,
                    content = trimmed,
                    attachmentJson = attachmentJson
                )
            }.onSuccess { conversationId ->
                _uiState.update {
                    it.copy(
                        currentConversationId = conversationId,
                        isSaving = false,
                        noticeMessage = "附件消息已处理"
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.readableMessage()
                    )
                }
            }
        }
    }

    fun regenerateLastAssistant() {
        val conversationId = _uiState.value.currentConversationId ?: return
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                repository.regenerateLastAssistant(conversationId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        noticeMessage = "AI 回复已重新生成"
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.readableMessage()
                    )
                }
            }
        }
    }

    fun retryAssistantMessage(messageId: String) {
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                repository.retryAssistantMessage(messageId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        noticeMessage = "AI 回复已重试"
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.readableMessage()
                    )
                }
            }
        }
    }

    fun stopAssistantResponse() {
        responseJob?.cancel()
        responseJob = null
        viewModelScope.launch {
            repository.stopStreamingAssistant(_uiState.value.currentConversationId)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    noticeMessage = "已中断本次 AI 回复"
                )
            }
        }
    }

    fun loadOlderMessages() {
        val conversationId = _uiState.value.currentConversationId ?: return
        viewModelScope.launch {
            repository.loadOlderMessages(conversationId)
        }
    }

    fun setClawMode(enabled: Boolean) {
        _uiState.update {
            it.copy(
                isClawMode = enabled,
                noticeMessage = if (enabled) {
                    "Claw 本地模式已开启，输入拍照、短信、下载等指令将直接调用本地能力"
                } else {
                    "已切回 Agent 联网模式"
                }
            )
        }
    }

    fun addClawUserCommand(content: String, resultHint: String? = null) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val conversationId = repository.addLocalMessage(
                    conversationId = _uiState.value.currentConversationId,
                    role = "user",
                    content = trimmed
                )
                resultHint?.takeIf { it.isNotBlank() }?.let { hint ->
                    repository.addLocalMessage(
                        conversationId = conversationId,
                        role = "claw",
                        content = hint
                    )
                }
                conversationId
            }.onSuccess { conversationId ->
                _uiState.update {
                    it.copy(
                        currentConversationId = conversationId,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.readableMessage()) }
            }
        }
    }

    fun addClawResult(content: String, attachmentJson: String? = null) {
        viewModelScope.launch {
            runCatching {
                repository.addLocalMessage(
                    conversationId = _uiState.value.currentConversationId,
                    role = "claw",
                    content = content,
                    attachmentJson = attachmentJson
                )
            }.onSuccess { conversationId ->
                _uiState.update {
                    it.copy(
                        currentConversationId = conversationId,
                        noticeMessage = "Claw 本地任务已写入对话"
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.readableMessage()) }
            }
        }
    }

    fun addClawExchange(
        userContent: String,
        resultContent: String,
        attachmentJson: String? = null
    ) {
        val trimmed = userContent.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val conversationId = repository.addLocalMessage(
                    conversationId = _uiState.value.currentConversationId,
                    role = "user",
                    content = trimmed
                )
                repository.addLocalMessage(
                    conversationId = conversationId,
                    role = "claw",
                    content = resultContent,
                    attachmentJson = attachmentJson
                )
                conversationId
            }.onSuccess { conversationId ->
                _uiState.update {
                    it.copy(
                        currentConversationId = conversationId,
                        errorMessage = null,
                        noticeMessage = "Claw 本地任务已写入对话"
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.readableMessage()) }
            }
        }
    }

    fun addClawPhotoResult(uri: android.net.Uri) {
        val context = getApplication<Application>()
        val attachmentJson = FileUtils.buildAttachmentJson(
            context = context,
            type = "image",
            uri = uri
        )
        addClawResult(
            content = "Claw 已完成自动拍照，图片如下。",
            attachmentJson = attachmentJson
        )
    }

    fun logClawCommand(
        type: String,
        status: String,
        description: String
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            agentTaskDao.upsertTask(
                AgentTaskEntity(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    status = status,
                    description = description,
                    createdAt = now,
                    completedAt = now
                )
            )
        }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun showNotice(message: String) {
        _uiState.update { it.copy(noticeMessage = message) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
    }

    fun activateModelProfile(profileId: String) {
        viewModelScope.launch {
            runCatching {
                settingsStore.setActiveModelProfile(profileId)
            }.onSuccess {
                val profile = _uiState.value.modelProfiles.firstOrNull { it.id == profileId }
                _uiState.update {
                    it.copy(noticeMessage = "已切换为 ${profile?.name ?: "所选模型"}")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.readableMessage()) }
            }
        }
    }

    fun updateActiveModelName(model: String) {
        viewModelScope.launch {
            runCatching {
                settingsStore.updateActiveProfileModel(model)
            }.onSuccess {
                _uiState.update {
                    it.copy(noticeMessage = "当前服务模型已切换为 ${model.trim()}")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.readableMessage()) }
            }
        }
    }

    fun updateActiveReasoningEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                settingsStore.updateActiveReasoningEnabled(enabled)
            }.onSuccess {
                _uiState.update {
                    it.copy(noticeMessage = if (enabled) "思考模式已开启" else "思考模式已关闭")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(errorMessage = error.readableMessage()) }
            }
        }
    }

    private fun observeConversations() {
        viewModelScope.launch {
            repository.observeConversations().collect { conversations ->
                _uiState.update { state ->
                    val currentStillExists = conversations.any { it.id == state.currentConversationId }
                    state.copy(
                        conversations = conversations,
                        currentConversationId = when {
                            currentStillExists -> state.currentConversationId
                            conversations.isNotEmpty() -> conversations.first().id
                            else -> null
                        }
                    )
                }
            }
        }
    }

    private fun observeModelProfiles() {
        viewModelScope.launch {
            settingsStore.modelProfiles.collect { profiles ->
                _uiState.update { it.copy(modelProfiles = profiles) }
            }
        }
        viewModelScope.launch {
            settingsStore.activeModelProfile.collect { profile ->
                _uiState.update { it.copy(activeModelProfile = profile) }
            }
        }
    }

    private fun observeClawCommandLogs() {
        viewModelScope.launch {
            agentTaskDao.observeTasks().collect { tasks ->
                _uiState.update { it.copy(clawTasks = tasks) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePagination() {
        viewModelScope.launch {
            repository.visibleMessageLimit.collect { limit ->
                _uiState.update { it.copy(visibleMessageLimit = limit) }
            }
        }
        viewModelScope.launch {
            uiState
                .map { it.currentConversationId }
                .distinctUntilChanged()
                .flatMapLatest { repository.observeMessageCount(it) }
                .collect { count ->
                    _uiState.update { it.copy(totalMessageCount = count) }
                }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCurrentMessages() {
        viewModelScope.launch {
            uiState
                .map { it.currentConversationId }
                .distinctUntilChanged()
                .flatMapLatest { repository.observeMessages(it) }
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
        }
    }

    private fun Throwable.readableMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: "本地会话操作失败"
    }
}

data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<MessageEntity> = emptyList(),
    val clawTasks: List<AgentTaskEntity> = emptyList(),
    val modelProfiles: List<ModelProfile> = emptyList(),
    val activeModelProfile: ModelProfile? = null,
    val isClawMode: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val visibleMessageLimit: Int = 50,
    val totalMessageCount: Int = 0,
    val isExporting: Boolean = false,
    val pendingExport: ChatExportUiState? = null
) {
    val currentConversation: ConversationEntity?
        get() = conversations.firstOrNull { it.id == currentConversationId }
    val hasOlderMessages: Boolean
        get() = totalMessageCount > visibleMessageLimit
}

data class ChatExportUiState(
    val fileName: String,
    val markdown: String
)
