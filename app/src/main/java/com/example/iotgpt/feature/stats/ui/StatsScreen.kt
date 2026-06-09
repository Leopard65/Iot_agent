package com.example.iotgpt.feature.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotgpt.core.components.AppPage
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Statistics dashboard for usage and health monitoring.
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    AppPage(
        title = "数据中心",
        subtitle = "会话、模型调用、智能体任务与网络状态总览",
        trailing = {
            StatusPill(
                text = if (uiState.networkStatus.isConnected) uiState.networkStatus.label else "无网络",
                tone = if (uiState.networkStatus.isConnected) StatusTone.Success else StatusTone.Neutral
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard("会话", uiState.totalConversations.toString(), Modifier.weight(1f))
            MetricCard("消息", uiState.totalMessages.toString(), Modifier.weight(1f))
            MetricCard("今日", uiState.todayMessages.toString(), Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard("用户", uiState.userMessages.toString(), Modifier.weight(1f))
            MetricCard("AI", uiState.assistantMessages.toString(), Modifier.weight(1f))
            MetricCard("调用", uiState.totalModelCalls.toString(), Modifier.weight(1f))
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "对话条目占比",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            MessageRolePieChart(
                userMessages = uiState.userMessages,
                assistantMessages = uiState.assistantMessages
            )
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "最近 7 天消息趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val trend = uiState.messageTrend.ifEmpty {
                listOf(DailyMessageCount("暂无", 0, 0f))
            }
            trend.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.label, style = MaterialTheme.typography.labelMedium)
                        Text("${item.count} 条", style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "模型使用统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    uiState.apiStatusLabel,
                    tone = if (uiState.lastApiError == null) StatusTone.Primary else StatusTone.Neutral
                )
                StatusPill("Token/字符 ${uiState.totalTokens}")
            }
            ModelConfigSummary(uiState)
            Text("最近请求：${formatTime(uiState.lastModelRequestAt)}")
            ModelUsageDistributionChart(uiState.modelUsageDistribution)
            uiState.lastApiError?.let { error ->
                Text(
                    text = "最近失败：$error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (uiState.modelUsage.isEmpty()) {
                Text(
                    text = "暂无模型调用记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "智能体与网络状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = if (uiState.networkStatus.isConnected) "网络正常" else "网络不可用",
                    tone = if (uiState.networkStatus.isConnected) StatusTone.Success else StatusTone.Neutral
                )
                StatusPill("智能体任务 ${uiState.agentTaskCount}")
                StatusPill("已完成 ${uiState.completedAgentTasks}")
            }
            Text("连接类型：${uiState.networkStatus.label}")
        }
    }
}

@Composable
private fun MessageRolePieChart(
    userMessages: Int,
    assistantMessages: Int
) {
    val total = userMessages + assistantMessages
    val userColor = MaterialTheme.colorScheme.primary
    val assistantColor = MaterialTheme.colorScheme.tertiary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(132.dp)) {
            if (total <= 0) {
                drawCircle(color = emptyColor)
            } else {
                val userSweep = userMessages.toFloat() / total.toFloat() * 360f
                drawArc(
                    color = userColor,
                    startAngle = -90f,
                    sweepAngle = userSweep,
                    useCenter = true
                )
                drawArc(
                    color = assistantColor,
                    startAngle = -90f + userSweep,
                    sweepAngle = 360f - userSweep,
                    useCenter = true
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PieLegendRow(userColor, "用户消息", userMessages, total)
            PieLegendRow(assistantColor, "AI 回复", assistantMessages, total)
            Text(
                text = "合计 $total 条",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PieLegendRow(
    color: Color,
    label: String,
    count: Int,
    total: Int
) {
    val percent = if (total <= 0) 0 else count * 100 / total
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "$count 条 · $percent%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UsageBarChart(
    title: String,
    buckets: List<UsageBucket>
) {
    val data = buckets.ifEmpty { listOf(UsageBucket("暂无", 0, 0f)) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        data.forEach { bucket ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(bucket.label, style = MaterialTheme.typography.labelMedium)
                    Text("${bucket.count} 次", style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { bucket.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ModelUsageDistributionChart(
    buckets: List<StackedUsageBucket>
) {
    val palette = listOf(
        Color(0xFF35527D),
        Color(0xFFFFC107),
        Color(0xFF90DCEB),
        Color(0xFF6C8B3F),
        Color(0xFFC46A4A),
        Color(0xFF7B61A8)
    )
    val data = buckets.ifEmpty {
        listOf(StackedUsageBucket("暂无", emptyList()))
    }
    val modelIds = data
        .flatMap { it.segments.map { segment -> segment.modelId } }
        .distinct()
    val maxTotal = data.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
    val total = data.sumOf { it.total }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "模型消耗分布",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Token/字符估算总计：${formatAmount(total)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val plotLeft = 10f
            val plotTop = 12f
            val plotRight = size.width - 8f
            val plotBottom = size.height - 16f
            val plotHeight = plotBottom - plotTop
            val plotWidth = plotRight - plotLeft
            val gridColor = Color(0xFFE7EAF0)

            repeat(5) { index ->
                val y = plotTop + plotHeight * index / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1f
                )
            }

            val slotWidth = plotWidth / data.size.coerceAtLeast(1)
            val barWidth = slotWidth * 0.62f
            data.forEachIndexed { index, bucket ->
                val x = plotLeft + slotWidth * index + (slotWidth - barWidth) / 2f
                var bottom = plotBottom
                bucket.segments.forEach { segment ->
                    val height = plotHeight * (segment.amount.toFloat() / maxTotal.toFloat())
                    val colorIndex = modelIds.indexOf(segment.modelId).coerceAtLeast(0)
                    drawRect(
                        color = palette[colorIndex % palette.size],
                        topLeft = Offset(x, bottom - height),
                        size = Size(barWidth, height)
                    )
                    bottom -= height
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEachIndexed { index, bucket ->
                Text(
                    text = if (index % 2 == 0 || data.size <= 5) bucket.label else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (modelIds.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                modelIds.chunked(2).forEach { rowModels ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowModels.forEach { modelId ->
                            LegendItem(
                                color = palette[modelIds.indexOf(modelId) % palette.size],
                                label = modelId
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawRect(color = color)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun ModelConfigSummary(uiState: StatsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "当前配置：${uiState.activeProfileName}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${uiState.activeProvider} · ${uiState.activeModel} · ${uiState.activeBaseUrlHost}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                text = if (uiState.activeApiKeyConfigured) "Key 已配置" else "Key 未配置",
                tone = if (uiState.activeApiKeyConfigured) StatusTone.Success else StatusTone.Neutral
            )
            StatusPill(
                text = if (uiState.activeSupportsVision) "图片输入已开启" else "图片输入未开启",
                tone = if (uiState.activeSupportsVision) StatusTone.Success else StatusTone.Neutral
            )
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    AppSectionCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(timestamp: Long?): String {
    if (timestamp == null) return "--"
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatAmount(value: Long): String {
    return when {
        value >= 10_000 -> "%.1f万".format(value / 10_000.0)
        else -> value.toString()
    }
}
