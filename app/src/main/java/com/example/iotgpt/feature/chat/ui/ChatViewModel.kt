package com.example.iotgpt.feature.chat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.iotgpt.core.database.AppDatabase
import com.example.iotgpt.core.database.entity.ConversationEntity
import com.example.iotgpt.core.database.entity.MessageEntity
import com.example.iotgpt.core.notification.NotificationHelper
import com.example.iotgpt.core.preferences.ModelProfile
import com.example.iotgpt.core.preferences.SettingsStore
import com.example.iotgpt.feature.chat.ChatRepository
import com.example.iotgpt.feature.chat.ChatRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val repository: ChatRepository = ChatRepositoryImpl(
        database = AppDatabase.getInstance(application),
        settingsStore = settingsStore,
        appContext = application,
        notificationHelper = NotificationHelper(application)
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        observeConversations()
        observeCurrentMessages()
        observeModelProfiles()
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

    fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.readableMessage()
                    )
                }
            }
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
    val modelProfiles: List<ModelProfile> = emptyList(),
    val activeModelProfile: ModelProfile? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val noticeMessage: String? = null
) {
    val currentConversation: ConversationEntity?
        get() = conversations.firstOrNull { it.id == currentConversationId }
}
