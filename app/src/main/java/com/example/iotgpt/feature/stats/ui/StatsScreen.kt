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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.iotgpt.core.components.AppPage
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone
import com.example.iotgpt.core.database.dao.ModelUsageSummary
import com.example.iotgpt.ui.theme.LotColors
import com.example.iotgpt.ui.theme.LotMotion
import com.example.iotgpt.ui.theme.LotRadius
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        MetricsGrid(uiState)

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
private fun MetricsGrid(uiState: StatsUiState) {
    val metrics = listOf(
        "会话数" to uiState.totalConversations.toString(),
        "消息数" to uiState.totalMessages.toString(),
        "今日消息" to uiState.todayMessages.toString(),
        "模型调用" to uiState.totalModelCalls.toString()
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (label, value) ->
                    MetricCard(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private const val CHART_MAX_MODELS = 5
private const val OTHER_MODEL_LABEL = "其他" // mirrors StatsViewModel.buildModelBuckets

private data class ChartInsightRow(
    val tag: String,
    val color: Color,
    val label: String,
    val value: String
)

private data class ChartInsight(
    val title: String,
    val total: String,
    val rows: List<ChartInsightRow>
)

private fun modelTag(index: Int): String = "M${index + 1}"

/** Pure: which density hint (if any) to show under a chart. */
internal fun chartDensityNote(
    seriesCount: Int,
    pointCount: Int,
    aggregated: Boolean,
    maxModels: Int
): String? = when {
    aggregated -> "仅显示调用最多的 $maxModels 个模型，其余归入“其他”"
    pointCount <= 2 || seriesCount <= 1 -> "数据较少，继续使用后趋势更清晰"
    else -> null
}

private fun densityNoteFor(uiState: StatsUiState, mode: ModelChartMode): String? {
    return when (mode) {
        ModelChartMode.Consumption -> {
            val buckets = uiState.modelUsageDistribution
            val ids = buckets.flatMap { it.segments.map { s -> s.modelId } }.distinct()
            chartDensityNote(ids.size, buckets.size, ids.contains(OTHER_MODEL_LABEL), CHART_MAX_MODELS)
        }
        ModelChartMode.Trend -> {
            val buckets = uiState.modelCallTrend
            val ids = buckets.flatMap { it.segments.map { s -> s.modelId } }.distinct()
            chartDensityNote(ids.size, buckets.size, ids.contains(OTHER_MODEL_LABEL), CHART_MAX_MODELS)
        }
        ModelChartMode.CountDistribution -> {
            val n = uiState.modelUsage.count { it.callCount > 0 }
            chartDensityNote(n, n, false, CHART_MAX_MODELS)
        }
        ModelChartMode.Ranking -> {
            val n = uiState.modelUsage.size
            chartDensityNote(n.coerceAtMost(8), n, n > 8, CHART_MAX_MODELS)
        }
    }
}

@Composable
private fun ChartInsightCard(insight: ChartInsight, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LotRadius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = insight.total,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "关闭选中详情" }
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (insight.rows.isEmpty()) {
            Text(
                text = "无调用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            insight.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.tag,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp)
                    )
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = row.color)
                    }
                    Text(
                        text = row.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelAnalyticsPanel(
    uiState: StatsUiState,
    selectedMode: ModelChartMode,
    onModeSelected: (ModelChartMode) -> Unit
) {
    var selectedInsight by remember(selectedMode) { mutableStateOf<ChartInsight?>(null) }

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
                    text = "Token/字符 总计 ${formatAmount(uiState.totalTokens)}$estimateSuffix",
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
            densityNoteFor(uiState, selectedMode)?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            var lastInsight by remember { mutableStateOf<ChartInsight?>(null) }
            LaunchedEffect(selectedInsight) { selectedInsight?.let { lastInsight = it } }
            AnimatedVisibility(
                visible = selectedInsight != null,
                enter = fadeIn(tween(LotMotion.normal)) + expandVertically(tween(LotMotion.normal)),
                exit = fadeOut(tween(LotMotion.fast)) + shrinkVertically(tween(LotMotion.fast))
            ) {
                lastInsight?.let { insight -> ChartInsightCard(insight) { selectedInsight = null } }
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
    onInsight: (ChartInsight) -> Unit
) {
    val palette = chartPalette()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
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
                .semantics { contentDescription = "模型消耗分布柱状图，${data.size} 个时间段，Token/字符估算总计 ${formatAmount(total)}" }
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
                        val rows = bucket.segments
                            .sortedByDescending { it.amount }
                            .map { segment ->
                                val ci = modelIds.indexOf(segment.modelId).coerceAtLeast(0)
                                ChartInsightRow(
                                    tag = modelTag(ci),
                                    color = palette[ci % palette.size],
                                    label = segment.modelId,
                                    value = formatAmount(segment.amount)
                                )
                            }
                        onInsight(
                            ChartInsight(
                                title = bucket.label,
                                total = "总计 ${formatAmount(bucket.total)}",
                                rows = rows
                            )
                        )
                    }
                }
        ) {
            val plotLeft = 10f
            val plotTop = 12f
            val plotRight = size.width - 8f
            val plotBottom = size.height - 16f
            val plotHeight = plotBottom - plotTop
            val plotWidth = plotRight - plotLeft

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
                                label = modelId,
                                code = modelTag(modelIds.indexOf(modelId))
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
    onInsight: (ChartInsight) -> Unit
) {
    val palette = chartPalette()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
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
                .semantics { contentDescription = "模型调用趋势折线图，总计 $total 次" }
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
                        val rows = bucket.segments
                            .sortedByDescending { it.amount }
                            .map { segment ->
                                val ci = modelIds.indexOf(segment.modelId).coerceAtLeast(0)
                                ChartInsightRow(
                                    tag = modelTag(ci),
                                    color = palette[ci % palette.size],
                                    label = segment.modelId,
                                    value = "${segment.amount} 次"
                                )
                            }
                        onInsight(
                            ChartInsight(
                                title = bucket.label,
                                total = "总计 ${bucket.total} 次",
                                rows = rows
                            )
                        )
                    }
                }
        ) {
            val plotLeft = 10f
            val plotTop = 12f
            val plotRight = size.width - 8f
            val plotBottom = size.height - 18f
            val plotHeight = plotBottom - plotTop
            val plotWidth = plotRight - plotLeft

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
    onInsight: (ChartInsight) -> Unit
) {
    val palette = chartPalette()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
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
                    .semantics { contentDescription = "模型调用次数占比环形图，总计 $total 次" }
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
                                    onInsight(
                                        ChartInsight(
                                            title = item.modelId,
                                            total = "${item.amount} 次 · $percent%",
                                            rows = listOf(
                                                ChartInsightRow(
                                                    tag = modelTag(index),
                                                    color = palette[index % palette.size],
                                                    label = item.modelId,
                                                    value = "${item.amount} 次 · $percent%"
                                                )
                                            )
                                        )
                                    )
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
                        color = trackColor,
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
                    value = "${item.amount} 次 · $percent%",
                    code = modelTag(index)
                )
            }
        }
    }
}

@Composable
private fun ModelCallRankingChart(
    usage: List<ModelUsageSummary>,
    onInsight: (ChartInsight) -> Unit
) {
    val palette = chartPalette()
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
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
                        text = "${modelTag(index)} · ${item.modelId}",
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
                        .semantics { contentDescription = "${item.modelId}，调用 ${item.callCount} 次" }
                        .pointerInput(item) {
                            detectTapGestures {
                                onInsight(
                                    ChartInsight(
                                        title = item.modelId,
                                        total = "${item.callCount} 次",
                                        rows = listOf(
                                            ChartInsightRow(
                                                tag = modelTag(index),
                                                color = palette[index % palette.size],
                                                label = "累计 token/字符",
                                                value = formatAmount(item.totalTokens)
                                            )
                                        )
                                    )
                                )
                            }
                        }
                ) {
                    drawRoundRect(
                        color = trackColor,
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
                        label = modelId,
                        code = modelTag(modelIds.indexOf(modelId))
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
    value: String,
    code: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp)
        )
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
    label: String,
    code: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    AppSectionCard(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    return LotColors.ChartPalette
}
