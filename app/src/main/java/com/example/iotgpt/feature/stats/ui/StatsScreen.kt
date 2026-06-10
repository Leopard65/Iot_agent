package com.example.iotgpt.feature.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotgpt.core.components.AppPage
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone
import com.example.iotgpt.core.database.dao.ModelUsageSummary
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
    var chartMode by rememberSaveable { mutableStateOf(ModelChartMode.Consumption) }

    AppPage(
        title = "模型数据分析",
        subtitle = "消耗分布、调用趋势与模型排行",
        trailing = {
            StatusPill(
                text = if (uiState.networkStatus.isConnected) uiState.networkStatus.label else "无网络",
                tone = if (uiState.networkStatus.isConnected) StatusTone.Success else StatusTone.Neutral
            )
        }
    ) {
        ModelAnalyticsPanel(
            uiState = uiState,
            selectedMode = chartMode,
            onModeSelected = { chartMode = it }
        )

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "链路状态",
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
            Text(
                text = "最近请求：${formatTime(uiState.lastModelRequestAt)} · 当前模型：${uiState.activeModel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            uiState.lastApiError?.let { error ->
                Text(
                    text = "最近失败：$error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private enum class ModelChartMode(
    val label: String,
    val title: String
) {
    Consumption("消耗分布", "模型消耗分布"),
    Trend("调用趋势", "模型调用趋势"),
    CountDistribution("次数分布", "模型调用次数占比"),
    Ranking("次数排行", "模型调用次数排行")
}

@Composable
private fun ModelAnalyticsPanel(
    uiState: StatsUiState,
    selectedMode: ModelChartMode,
    onModeSelected: (ModelChartMode) -> Unit
) {
    var selectedInsight by rememberSaveable(selectedMode) { mutableStateOf<String?>(null) }

    AppSectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val estimateSuffix = if (uiState.estimatedModelUsageCount > 0) {
                    " · 含 ${uiState.estimatedModelUsageCount} 条估算"
                } else {
                    " · 精确"
                }
                Text(
                    text = selectedMode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "上传 ${formatAmount(uiState.totalPromptTokens.toLong())} · 下载 ${formatAmount(uiState.totalCompletionTokens.toLong())} · 总计 ${formatAmount(uiState.totalTokens)}$estimateSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(
                uiState.apiStatusLabel,
                tone = if (uiState.lastApiError == null) StatusTone.Primary else StatusTone.Neutral
            )
        }

        ChartModeSelector(selectedMode, onModeSelected)

        if (uiState.modelUsage.isEmpty()) {
            EmptyChartState()
        } else {
            when (selectedMode) {
                ModelChartMode.Consumption -> ModelUsageDistributionChart(
                    buckets = uiState.modelUsageDistribution,
                    onInsight = { selectedInsight = it }
                )
                ModelChartMode.Trend -> ModelCallTrendChart(
                    buckets = uiState.modelCallTrend,
                    onInsight = { selectedInsight = it }
                )
                ModelChartMode.CountDistribution -> ModelCallCountDonutChart(
                    segments = uiState.modelUsage.map {
                        UsageSegment(modelId = it.modelId, amount = it.callCount.toLong())
                    },
                    onInsight = { selectedInsight = it }
                )
                ModelChartMode.Ranking -> ModelCallRankingChart(
                    usage = uiState.modelUsage,
                    onInsight = { selectedInsight = it }
                )
            }
            selectedInsight?.let {
                Text(
                    text = "选中：$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChartModeSelector(
    selectedMode: ModelChartMode,
    onModeSelected: (ModelChartMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ModelChartMode.entries.chunked(2).forEach { rowModes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowModes.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
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
    buckets: List<StackedUsageBucket>,
    onInsight: (String) -> Unit
) {
    val palette = chartPalette()
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
            text = "最近有调用记录的时间段",
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
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val plotLeft = 10f
                        val plotRight = size.width - 8f
                        val plotWidth = plotRight - plotLeft
                        val slotWidth = plotWidth / data.size.coerceAtLeast(1)
                        val index = ((offset.x - plotLeft) / slotWidth)
                            .toInt()
                            .coerceIn(0, data.lastIndex)
                        val bucket = data[index]
                        val detail = bucket.segments
                            .sortedByDescending { it.amount }
                            .joinToString("，") { "${it.modelId} ${formatAmount(it.amount)}" }
                            .ifBlank { "无调用" }
                        onInsight("${bucket.label} · 总计 ${formatAmount(bucket.total)} · $detail")
                    }
                }
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
private fun ModelCallTrendChart(
    buckets: List<StackedUsageBucket>,
    onInsight: (String) -> Unit
) {
    val palette = chartPalette()
    val data = buckets.ifEmpty { listOf(StackedUsageBucket("暂无", emptyList())) }
    val modelIds = data
        .flatMap { it.segments.map { segment -> segment.modelId } }
        .distinct()
    val maxValue = data
        .flatMap { it.segments }
        .maxOfOrNull { it.amount }
        ?.coerceAtLeast(1L)
        ?: 1L
    val total = data.sumOf { it.total }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "按时间分模型调用次数",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "总计：$total 次",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val plotLeft = 10f
                        val plotRight = size.width - 8f
                        val plotWidth = plotRight - plotLeft
                        val slotWidth = if (data.size <= 1) plotWidth else plotWidth / (data.size - 1)
                        val index = if (data.size <= 1) {
                            0
                        } else {
                            ((offset.x - plotLeft + slotWidth / 2f) / slotWidth)
                                .toInt()
                                .coerceIn(0, data.lastIndex)
                        }
                        val bucket = data[index]
                        val detail = bucket.segments
                            .sortedByDescending { it.amount }
                            .joinToString("，") { "${it.modelId} ${it.amount} 次" }
                            .ifBlank { "无调用" }
                        onInsight("${bucket.label} · 总计 ${bucket.total} 次 · $detail")
                    }
                }
        ) {
            val plotLeft = 10f
            val plotTop = 12f
            val plotRight = size.width - 8f
            val plotBottom = size.height - 18f
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

            val slotWidth = if (data.size <= 1) plotWidth else plotWidth / (data.size - 1)
            modelIds.forEachIndexed { modelIndex, modelId ->
                val color = palette[modelIndex % palette.size]
                val path = Path()
                data.forEachIndexed { index, bucket ->
                    val amount = bucket.segments.firstOrNull { it.modelId == modelId }?.amount ?: 0L
                    val x = if (data.size <= 1) center.x else plotLeft + slotWidth * index
                    val y = plotBottom - plotHeight * (amount.toFloat() / maxValue.toFloat())
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    if (amount > 0L) {
                        drawCircle(color = color, radius = 3.2.dp.toPx(), center = Offset(x, y))
                    }
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        TimeAxisLabels(data)
        ChartLegend(modelIds = modelIds, palette = palette)
    }
}

@Composable
private fun ModelCallCountDonutChart(
    segments: List<UsageSegment>,
    onInsight: (String) -> Unit
) {
    val palette = chartPalette()
    val data = segments.filter { it.amount > 0L }
    val total = data.sumOf { it.amount }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(180.dp)
                    .pointerInput(data, total) {
                        detectTapGestures { offset ->
                            if (total <= 0L) return@detectTapGestures
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val degrees = Math.toDegrees(
                                kotlin.math.atan2(
                                    (offset.y - centerY).toDouble(),
                                    (offset.x - centerX).toDouble()
                                )
                            ).toFloat()
                            val sweepPosition = (degrees + 90f + 360f) % 360f
                            var consumed = 0f
                            data.forEachIndexed { index, item ->
                                val sweep = item.amount.toFloat() / total.toFloat() * 360f
                                if (sweepPosition >= consumed && sweepPosition <= consumed + sweep) {
                                    val percent = item.amount * 100 / total
                                    onInsight("${item.modelId} · ${item.amount} 次 · $percent%")
                                    return@detectTapGestures
                                }
                                consumed += sweep
                            }
                        }
                    }
            ) {
                val strokeWidth = 24.dp.toPx()
                if (total <= 0L) {
                    drawCircle(
                        color = Color(0xFFE7EAF0),
                        radius = size.minDimension / 2f - strokeWidth,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    var start = -90f
                    data.forEachIndexed { index, item ->
                        val sweep = item.amount.toFloat() / total.toFloat() * 360f
                        drawArc(
                            color = palette[index % palette.size],
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "TOTAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.forEachIndexed { index, item ->
                val percent = if (total == 0L) 0 else item.amount * 100 / total
                LegendValueRow(
                    color = palette[index % palette.size],
                    label = item.modelId,
                    value = "${item.amount} 次 · $percent%"
                )
            }
        }
    }
}

@Composable
private fun ModelCallRankingChart(
    usage: List<ModelUsageSummary>,
    onInsight: (String) -> Unit
) {
    val palette = chartPalette()
    val data = usage.sortedByDescending { it.callCount }.take(8)
    val max = data.maxOfOrNull { it.callCount }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        data.forEachIndexed { index, item ->
            val progress = item.callCount.toFloat() / max.toFloat()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.modelId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "${item.callCount} 次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(item) {
                            detectTapGestures {
                                onInsight(
                                    "${item.modelId} · ${item.callCount} 次 · 总计 ${formatAmount(item.totalTokens)}"
                                )
                            }
                        }
                ) {
                    drawRoundRect(
                        color = Color(0xFFE7EAF0),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                    drawRoundRect(
                        color = palette[index % palette.size],
                        size = Size(size.width * progress, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChartState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "暂无模型调用记录",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "完成一次对话后这里会显示图表",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeAxisLabels(data: List<StackedUsageBucket>) {
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
}

@Composable
private fun ChartLegend(
    modelIds: List<String>,
    palette: List<Color>
) {
    if (modelIds.isEmpty()) return
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

@Composable
private fun LegendValueRow(
    color: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawCircle(color = color)
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

private fun chartPalette(): List<Color> {
    return listOf(
        Color(0xFF35527D),
        Color(0xFFFFC107),
        Color(0xFF90DCEB),
        Color(0xFF6C8B3F),
        Color(0xFFC46A4A),
        Color(0xFF7B61A8)
    )
}
