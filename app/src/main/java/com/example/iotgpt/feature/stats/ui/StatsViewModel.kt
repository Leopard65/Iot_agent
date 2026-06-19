package com.example.iotgpt.feature.stats.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.example.iotgpt.core.database.AppDatabase
import com.example.iotgpt.core.database.dao.ModelUsageSummary
import com.example.iotgpt.core.database.entity.ModelUsageEntity
import com.example.iotgpt.core.network.NetworkMonitor
import com.example.iotgpt.core.network.NetworkStatus
import com.example.iotgpt.core.preferences.SettingsStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Aggregates Room statistics and network status for the dashboard.
 */
class StatsViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val modelUsageDao = database.modelUsageDao()
    private val agentTaskDao = database.agentTaskDao()
    private val networkMonitor = NetworkMonitor(application)
    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        observeCounters()
        observeModelUsage()
        observeAgentTasks()
        observeNetwork()
        observeModelSettings()
    }

    private fun observeCounters() {
        viewModelScope.launch {
            conversationDao.observeConversationCount().collect { count ->
                _uiState.update { it.copy(totalConversations = count) }
            }
        }
        viewModelScope.launch {
            messageDao.observeMessageCount().collect { count ->
                _uiState.update { it.copy(totalMessages = count) }
            }
        }
        viewModelScope.launch {
            messageDao.observeMessageCountSince(startOfTodayMillis()).collect { count ->
                _uiState.update { it.copy(todayMessages = count) }
            }
        }
        viewModelScope.launch {
            messageDao.observeMessageCountByRole("user").collect { count ->
                _uiState.update { it.copy(userMessages = count) }
            }
        }
        viewModelScope.launch {
            messageDao.observeMessageCountByRole("assistant").collect { count ->
                _uiState.update { it.copy(assistantMessages = count) }
            }
        }
        viewModelScope.launch {
            messageDao.observeMessageTimesSince(startOfRecentDaysMillis(DAYS_IN_TREND))
                .map { times -> buildTrend(times) }
                .flowOn(Dispatchers.Default)
                .collect { trend ->
                    _uiState.update { it.copy(messageTrend = trend) }
                }
        }
    }

    private fun observeModelUsage() {
        viewModelScope.launch {
            modelUsageDao.observeUsageByModel().collect { usage ->
                _uiState.update {
                    it.copy(
                        modelUsage = usage,
                        totalModelCalls = usage.sumOf { item -> item.callCount },
                        totalTokens = usage.sumOf { item -> item.totalTokens }
                    )
                }
            }
        }
        viewModelScope.launch {
            modelUsageDao.observeLastRequestAt().collect { lastRequest ->
                _uiState.update { it.copy(lastModelRequestAt = lastRequest) }
            }
        }
        viewModelScope.launch {
            modelUsageDao.observeUsageRecords()
                .map { records ->
                    ModelUsageAggregate(
                        byDay = buildUsageByDay(records),
                        byHour = buildUsageByHour(records),
                        distribution = buildModelUsageDistribution(records),
                        callTrend = buildModelCallTrend(records),
                        promptTokens = records.sumOf { record -> record.promptTokens },
                        completionTokens = records.sumOf { record -> record.completionTokens },
                        estimatedCount = records.count { record -> record.isEstimated }
                    )
                }
                .flowOn(Dispatchers.Default)
                .collect { aggregate ->
                    _uiState.update {
                        it.copy(
                            modelUsageByDay = aggregate.byDay,
                            modelUsageByHour = aggregate.byHour,
                            modelUsageDistribution = aggregate.distribution,
                            modelCallTrend = aggregate.callTrend,
                            totalPromptTokens = aggregate.promptTokens,
                            totalCompletionTokens = aggregate.completionTokens,
                            estimatedModelUsageCount = aggregate.estimatedCount
                        )
                    }
                }
        }
    }

    private fun observeAgentTasks() {
        viewModelScope.launch {
            agentTaskDao.observeTasks().collect { tasks ->
                _uiState.update {
                    it.copy(
                        agentTaskCount = tasks.size,
                        completedAgentTasks = tasks.count { task -> task.completedAt != null }
                    )
                }
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.observeNetworkStatus().collect { status ->
                _uiState.update { it.copy(networkStatus = status) }
            }
        }
    }

    private fun observeModelSettings() {
        viewModelScope.launch {
            settingsStore.activeModelProfile.collect { profile ->
                _uiState.update {
                    it.copy(
                        activeProfileName = profile.name,
                        activeProvider = profile.provider,
                        activeModel = profile.model,
                        activeBaseUrlHost = profile.baseUrl.toHostLabel(),
                        activeApiKeyConfigured = profile.apiKey.isNotBlank(),
                        activeSupportsVision = profile.supportsVision
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsStore.lastApiError.collect { error ->
                _uiState.update { it.copy(lastApiError = error) }
            }
        }
    }

    private fun String.toHostLabel(): String {
        return runCatching {
            toUri().host
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: trim().ifBlank { "--" }
    }

    private fun buildTrend(times: List<Long>): List<DailyMessageCount> {
        val dayStarts = (DAYS_IN_TREND - 1 downTo 0).map { daysAgo ->
            startOfDayMillis(daysAgo)
        }
        val counts = dayStarts.mapIndexed { index, start ->
            val end = dayStarts.getOrNull(index + 1) ?: (start + ONE_DAY_MILLIS)
            times.count { it >= start && it < end }
        }
        val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        return dayStarts.mapIndexed { index, start ->
            DailyMessageCount(
                label = dayFormat().format(Date(start)),
                count = counts[index],
                progress = counts[index].toFloat() / max.toFloat()
            )
        }
    }

    private fun buildUsageByDay(records: List<ModelUsageEntity>): List<UsageBucket> {
        val dayStarts = (DAYS_IN_TREND - 1 downTo 0).map { daysAgo ->
            startOfDayMillis(daysAgo)
        }
        val counts = dayStarts.mapIndexed { index, start ->
            val end = dayStarts.getOrNull(index + 1) ?: (start + ONE_DAY_MILLIS)
            records.count { it.createdAt >= start && it.createdAt < end }
        }
        return buildBuckets(dayStarts.map { dayFormat().format(Date(it)) }, counts)
    }

    private fun buildUsageByHour(records: List<ModelUsageEntity>): List<UsageBucket> {
        val hourStarts = (HOURS_IN_USAGE - 1 downTo 0).map { hoursAgo ->
            startOfHourMillis(hoursAgo)
        }
        val counts = hourStarts.map { start ->
            val end = start + ONE_HOUR_MILLIS
            records.count { it.createdAt >= start && it.createdAt < end }
        }
        return buildBuckets(hourStarts.map { hourFormat().format(Date(it)) }, counts)
    }

    private fun buildModelUsageDistribution(records: List<ModelUsageEntity>): List<StackedUsageBucket> {
        return buildModelBuckets(records, amountSelector = { it.usageAmount() })
    }

    private fun buildModelCallTrend(records: List<ModelUsageEntity>): List<StackedUsageBucket> {
        return buildModelBuckets(records, amountSelector = { 1L })
    }

    private fun buildModelBuckets(
        records: List<ModelUsageEntity>,
        amountSelector: (ModelUsageEntity) -> Long
    ): List<StackedUsageBucket> {
        val hourStarts = records
            .map { it.createdAt.toHourStartMillis() }
            .distinct()
            .sorted()
            .takeLast(HOURS_IN_DISTRIBUTION)
        if (hourStarts.isEmpty()) return emptyList()
        val recentRecords = records.filter { it.createdAt.toHourStartMillis() in hourStarts }
        val topModels = recentRecords
            .groupBy { it.modelId }
            .mapValues { (_, items) -> items.sumOf(amountSelector) }
            .toList()
            .sortedByDescending { it.second }
            .take(MAX_MODELS_IN_CHART)
            .map { it.first }

        return hourStarts.map { start ->
            val end = start + ONE_HOUR_MILLIS
            val recordsInBucket = recentRecords.filter { it.createdAt >= start && it.createdAt < end }
            val grouped = recordsInBucket
                .groupBy { record ->
                    if (record.modelId in topModels) record.modelId else "其他"
                }
                .mapValues { (_, items) -> items.sumOf(amountSelector) }
            StackedUsageBucket(
                label = distributionFormat().format(Date(start)),
                segments = grouped
                    .toList()
                    .sortedByDescending { it.second }
                    .map { UsageSegment(modelId = it.first, amount = it.second) }
            )
        }
    }

    private fun buildBuckets(labels: List<String>, counts: List<Int>): List<UsageBucket> {
        val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        return labels.mapIndexed { index, label ->
            UsageBucket(
                label = label,
                count = counts[index],
                progress = counts[index].toFloat() / max.toFloat()
            )
        }
    }

    private fun startOfTodayMillis(): Long = startOfDayMillis(0)

    private fun startOfRecentDaysMillis(days: Int): Long = startOfDayMillis(days - 1)

    private fun startOfDayMillis(daysAgo: Int): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun startOfHourMillis(hoursAgo: Int): Long {
        return Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, -hoursAgo)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // 纯 epoch 运算截断到整点，避免对每条记录都新建 Calendar；整点偏移时区下与本地结果一致。
    private fun Long.toHourStartMillis(): Long = this - (this % ONE_HOUR_MILLIS)

    private fun dayFormat(): SimpleDateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())

    private fun hourFormat(): SimpleDateFormat = SimpleDateFormat("HH:00", Locale.getDefault())

    private fun distributionFormat(): SimpleDateFormat = SimpleDateFormat("MM-dd HH:00", Locale.getDefault())

    private fun ModelUsageEntity.usageAmount(): Long {
        return totalTokens.toLong().takeIf { it > 0L } ?: 1L
    }

    private data class ModelUsageAggregate(
        val byDay: List<UsageBucket>,
        val byHour: List<UsageBucket>,
        val distribution: List<StackedUsageBucket>,
        val callTrend: List<StackedUsageBucket>,
        val promptTokens: Int,
        val completionTokens: Int,
        val estimatedCount: Int
    )

    private companion object {
        const val DAYS_IN_TREND = 7
        const val HOURS_IN_USAGE = 12
        const val HOURS_IN_DISTRIBUTION = 10
        const val MAX_MODELS_IN_CHART = 5
        const val ONE_DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val ONE_HOUR_MILLIS = 60L * 60L * 1000L
    }
}

data class StatsUiState(
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    val todayMessages: Int = 0,
    val userMessages: Int = 0,
    val assistantMessages: Int = 0,
    val agentTaskCount: Int = 0,
    val completedAgentTasks: Int = 0,
    val totalModelCalls: Int = 0,
    val totalTokens: Long = 0,
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val estimatedModelUsageCount: Int = 0,
    val lastModelRequestAt: Long? = null,
    val activeProfileName: String = "DeepSeek",
    val activeProvider: String = "DeepSeek",
    val activeModel: String = "deepseek-chat",
    val activeBaseUrlHost: String = "api.deepseek.com",
    val activeApiKeyConfigured: Boolean = false,
    val activeSupportsVision: Boolean = false,
    val lastApiError: String? = null,
    val modelUsage: List<ModelUsageSummary> = emptyList(),
    val messageTrend: List<DailyMessageCount> = emptyList(),
    val modelUsageByDay: List<UsageBucket> = emptyList(),
    val modelUsageByHour: List<UsageBucket> = emptyList(),
    val modelUsageDistribution: List<StackedUsageBucket> = emptyList(),
    val modelCallTrend: List<StackedUsageBucket> = emptyList(),
    val networkStatus: NetworkStatus = NetworkStatus(false, "检测中")
) {
    val apiStatusLabel: String
        get() = when {
            lastApiError != null -> "API 异常"
            lastModelRequestAt == null -> "API 待测试"
            else -> "API 可用"
        }
}

data class DailyMessageCount(
    val label: String,
    val count: Int,
    val progress: Float
)

data class UsageBucket(
    val label: String,
    val count: Int,
    val progress: Float
)

data class UsageSegment(
    val modelId: String,
    val amount: Long
)

data class StackedUsageBucket(
    val label: String,
    val segments: List<UsageSegment>
) {
    val total: Long = segments.sumOf { it.amount }
}
