package com.example.iotgpt.feature.agent.ui

import android.app.Application
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.iotgpt.core.database.AppDatabase
import com.example.iotgpt.core.database.entity.AgentTaskEntity
import com.example.iotgpt.core.notification.NotificationHelper
import com.example.iotgpt.core.worker.DownloadWorker
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Coordinates classroom-safe local agent task demonstrations.
 */
class AgentViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val appContext = application
    private val agentTaskDao = AppDatabase.getInstance(application).agentTaskDao()
    private val notificationHelper = NotificationHelper(application)
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState

    init {
        viewModelScope.launch {
            agentTaskDao.observeTasks().collect { tasks ->
                _uiState.update { it.copy(tasks = tasks) }
            }
        }
    }

    fun updatePhoneNumber(value: String) {
        _uiState.update { it.copy(phoneNumber = value, statusMessage = null) }
    }

    fun updateSmsContent(value: String) {
        _uiState.update { it.copy(smsContent = value, statusMessage = null) }
    }

    fun updateDownloadUrl(value: String) {
        _uiState.update { it.copy(downloadUrl = value, statusMessage = null) }
    }

    fun updateAppLeapCommand(value: String) {
        _uiState.update { it.copy(appLeapCommand = value, statusMessage = null) }
    }

    fun sendSms() {
        val phone = _uiState.value.phoneNumber.trim()
        val content = _uiState.value.smsContent.trim()
        if (phone.isBlank() || content.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请填写测试手机号和短信内容") }
            return
        }

        viewModelScope.launch {
            val taskId = UUID.randomUUID().toString()
            runCatching {
                newSmsManager().sendTextMessage(phone, null, content, null, null)
                agentTaskDao.upsertTask(
                    AgentTaskEntity(
                        id = taskId,
                        type = "sms",
                        status = "completed",
                        description = "短信已发送到 $phone",
                        createdAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis()
                    )
                )
                notificationHelper.showTaskComplete(taskId, "短信任务完成", "短信已发送到 $phone")
            }.onSuccess {
                _uiState.update { it.copy(statusMessage = "短信任务完成") }
            }.onFailure { error ->
                agentTaskDao.upsertTask(
                    AgentTaskEntity(
                        id = taskId,
                        type = "sms",
                        status = "failed",
                        description = error.message ?: "短信发送失败",
                        createdAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis()
                    )
                )
                _uiState.update { it.copy(statusMessage = error.message ?: "短信发送失败") }
            }
        }
    }

    fun logCameraResult(uri: String, displayPath: String) {
        viewModelScope.launch {
            val taskId = UUID.randomUUID().toString()
            agentTaskDao.upsertTask(
                AgentTaskEntity(
                    id = taskId,
                    type = "camera",
                    status = "completed",
                    description = "拍照完成，保存位置：$displayPath",
                    createdAt = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis()
                )
            )
            notificationHelper.showTaskComplete(taskId, "拍照任务完成", "照片已保存到应用专属目录")
            _uiState.update {
                it.copy(
                    lastPhotoUri = uri,
                    lastPhotoPath = displayPath,
                    statusMessage = "拍照任务完成，照片已保存到：$displayPath"
                )
            }
        }
    }

    fun logCameraFailure(message: String = "拍照已取消或失败") {
        viewModelScope.launch {
            val taskId = UUID.randomUUID().toString()
            agentTaskDao.upsertTask(
                AgentTaskEntity(
                    id = taskId,
                    type = "camera",
                    status = "failed",
                    description = message,
                    createdAt = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(statusMessage = message) }
        }
    }

    fun startDownload() {
        val url = _uiState.value.downloadUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(statusMessage = "请输入下载 URL") }
            return
        }

        viewModelScope.launch {
            val taskId = UUID.randomUUID().toString()
            agentTaskDao.upsertTask(
                AgentTaskEntity(
                    id = taskId,
                    type = "download",
                    status = "running",
                    description = "下载中：$url",
                    createdAt = System.currentTimeMillis(),
                    completedAt = null
                )
            )

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().putString(DownloadWorker.KEY_URL, url).build())
                .build()
            workManager.enqueue(request)
            observeDownloadWork(taskId, request.id.toString())
            _uiState.update { it.copy(statusMessage = "下载任务已启动") }
        }
    }

    fun executeAppLeap(onResult: ((success: Boolean, message: String) -> Unit)? = null) {
        val command = _uiState.value.appLeapCommand.trim()
        if (command.isBlank()) {
            val message = "请输入链接或跨应用搜索指令"
            _uiState.update { it.copy(statusMessage = message) }
            onResult?.invoke(false, message)
            return
        }

        viewModelScope.launch {
            val taskId = UUID.randomUUID().toString()
            runCatching {
                val intent = buildAppLeapIntent(command)
                appContext.startActivity(intent)
                agentTaskDao.upsertTask(
                    AgentTaskEntity(
                        id = taskId,
                        type = "app-leap",
                        status = "completed",
                        description = "跨应用 Intent 已执行：$command",
                        createdAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis()
                    )
                )
                notificationHelper.showTaskComplete(taskId, "App-Leap 已执行", command)
            }.onSuccess {
                val message = "App-Leap 指令已跃迁"
                _uiState.update { it.copy(statusMessage = message) }
                onResult?.invoke(true, message)
            }.onFailure { error ->
                val message = error.message ?: "跨应用 Intent 执行失败"
                agentTaskDao.upsertTask(
                    AgentTaskEntity(
                        id = taskId,
                        type = "app-leap",
                        status = "failed",
                        description = message,
                        createdAt = System.currentTimeMillis(),
                        completedAt = System.currentTimeMillis()
                    )
                )
                _uiState.update { it.copy(statusMessage = message) }
                onResult?.invoke(false, message)
            }
        }
    }

    private fun buildAppLeapIntent(command: String): Intent {
        val lower = command.lowercase()
        val url = Regex("""https?://\S+""").find(command)?.value
        return when {
            url != null -> Intent(Intent.ACTION_VIEW, url.toUri())
            listOf("wifi", "wi-fi", "无线").any { lower.contains(it) } -> {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            listOf("蓝牙", "bluetooth").any { lower.contains(it) } -> {
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            }
            listOf("系统设置", "打开设置", "设置").any { lower.contains(it) } -> {
                Intent(Settings.ACTION_SETTINGS)
            }
            listOf("搜索", "search", "浏览器", "browser").any { lower.contains(it) } -> {
                Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, AppLeapCommandResolver.toSearchQuery(command))
            }
            listOf("打开", "open", "应用", "app").any { lower.contains(it) } -> {
                val target = AppLeapCommandResolver.extractAppLaunchTarget(command)
                    ?: throw IllegalArgumentException("未识别到要打开的应用名称")
                findLaunchIntent(target)
                    ?: throw IllegalStateException("未找到可打开的应用：$target。请确认已安装该应用，或直接输入应用包名。")
            }
            else -> Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, command)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun findLaunchIntent(target: String): Intent? {
        val packageManager = appContext.packageManager
        return AppLeapCommandResolver.packageCandidatesFor(target)
            .firstNotNullOfOrNull { packageName ->
                packageManager.getLaunchIntentForPackage(packageName)
            }
    }

    private fun observeDownloadWork(taskId: String, workId: String) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(UUID.fromString(workId)).collect { info ->
                if (info == null) return@collect
                val progress = info.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                _uiState.update { it.copy(downloadProgress = progress) }
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString(DownloadWorker.KEY_FILE_PATH).orEmpty()
                        agentTaskDao.updateTaskStatus(
                            id = taskId,
                            status = "completed",
                            description = "下载完成：$path",
                            completedAt = System.currentTimeMillis()
                        )
                        _uiState.update { it.copy(statusMessage = "下载任务完成", downloadProgress = 100) }
                    }
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED -> {
                        agentTaskDao.updateTaskStatus(
                            id = taskId,
                            status = "failed",
                            description = "下载失败或已取消",
                            completedAt = System.currentTimeMillis()
                        )
                        _uiState.update { it.copy(statusMessage = "下载失败或已取消") }
                    }
                    else -> Unit
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun newSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
    }
}

data class AgentUiState(
    val phoneNumber: String = "",
    val smsContent: String = "AIoT Assistant 短信演示",
    val downloadUrl: String = "",
    val downloadProgress: Int = 0,
    val lastPhotoUri: String? = null,
    val lastPhotoPath: String? = null,
    val appLeapCommand: String = "",
    val tasks: List<AgentTaskEntity> = emptyList(),
    val statusMessage: String? = null
)
