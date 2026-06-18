# 统计页"深化现有图表体验"设计文档

- 日期：2026-06-18
- 范围：UI 审读文档「优先级 4：统计页从"有图表"升级为"可洞察"」中的"深化现有图表"方向
- 状态：已与用户确认设计，待写实现计划

## 背景与现状

统计页 (`feature/stats/ui/StatsScreen.kt`，971 行) 已具备：顶部自动洞察卡 (`StatsInsightCard` / `buildStatsInsight`)、4 个图表模式（消耗分布 / 调用趋势 / 次数分布 / 次数排行）、文字图例、空状态 (`EmptyChartState`)、统一调色板 (`LotColors.ChartPalette`)。

仍存在的缺口（对照 UI 审读优先级 4）：

1. 点击图表后的 insight 只是一行文本 `Text("选中：$it")`（`StatsScreen.kt:290-296`），文档要求做成卡片/浮层。
2. 只有"空数据"一种状态，缺"少数据""大数据（聚合截断）"的明示。
3. 图表靠颜色区分模型，图例无稳定短码，违背"不要只靠颜色记忆"。
4. 网格线/轨道/空环色硬编码为 `Color(0xFFE7EAF0)`（`:485 :605 :695 :784`），是固定浅灰，暗色模式下突兀，且未走主题色——真实缺陷。

## 目标

- 把点击 insight 升级为结构化内联洞察卡。
- 为图表补"空 / 少 / 多"三态的可见反馈与文案。
- 图例与洞察卡引入稳定短码（M1/M2…），颜色不再是唯一区分。
- 修复硬编码网格色，改用主题自适应色，暗色模式正常。
- 把图表调色板精修为「和谐鲜明」的 6 色（方案 B），兼顾区分度与协调感。

## 非目标（YAGNI）

- 不新增创意图表（雷达 / 热力日历 / Claw 漏斗）——属"新增图表"方向，本次不做。
- `LotDesignTokens.kt` 只改 `LotColors.ChartPalette`（换成方案 B 6 色）；不新增 spacing/radius 等 token，网格/轨道色仍直接取 `MaterialTheme.colorScheme`。
- 不改数据采集 / DAO / Room；ViewModel 仅在需要时承载 `ChartInsight` 数据类，不改聚合逻辑。
- 不做底部 `ModalBottomSheet`（已确认用内联卡）。

## 设计细节

### 1. 结构化内联洞察卡

新增数据类，置于 `StatsScreen.kt`（**不放 ViewModel**：它携带 Compose `Color`，放 ViewModel 会让数据层耦合 UI 类型；这是纯展示模型）：

```kotlin
data class ChartInsightRow(
    val tag: String,        // 短码 M1 / M2…
    val color: Color,       // 与图例同色
    val label: String,      // 模型名
    val value: String       // "210 次" / "1.2万 token" 等
)

data class ChartInsight(
    val title: String,      // "06-18 14:00" 或 模型名
    val total: String,      // 主指标，如 "总计 320 次"
    val rows: List<ChartInsightRow>
)
```

- 把 4 个图表的 `onInsight: (String) -> Unit` 改为 `onInsight: (ChartInsight) -> Unit`，各图表点击时构造 `ChartInsight`。
- `ModelAnalyticsPanel` 中 `selectedInsight: String?` 改为 `ChartInsight?`，渲染成一张内联卡：标题 + 主指标 + 行列表（色点 + 短码 + 名称 + 值）+ 右上角 `✕` 清除。
- 用现有的 `AnimatedVisibility + tween(LotMotion.normal)` 做淡入展开（让 motion token 多一处落地）。
- 切换图表模式时清空选中（保留现有 `rememberSaveable(selectedMode)` 行为）。
- `✕` 按钮加 `contentDescription = "关闭选中详情"`（顺手补一处可访问性）。

### 2. 空 / 少 / 多三态

展示层基于现有 state 判断，不改 ViewModel：

- **空**：`uiState.modelUsage.isEmpty()` → 保留 `EmptyChartState`，文案微调。
- **少**：有效数据点 ≤ 2，或去重模型数 == 1 → 正常画图，但保证单点有可见圆点/柱子，并在图下方加一行提示「数据较少，继续使用后趋势更清晰」。
- **多**：去重模型数 > `MAX_MODELS_IN_CHART`(5)（即已产生"其他"聚合），或时间桶达到上限 → 加一行明示「仅显示调用最多的 5 个模型，其余归入"其他"」，把静默截断变成显式说明。

阈值固定为：少 = `≤ 2` 数据点或 `1` 个模型；多 = 模型数 `> 5`。

### 3. 短码标签

- 计算 `modelIds` 的稳定顺序后，第 i 个模型短码为 `"M${i+1}"`。
- `LegendItem` / `ChartLegend` / 甜甜圈与排行的图例行：在名称前展示短码。
- `ChartInsightRow.tag` 用同一短码。
- 不在 Canvas 柱/线上叠文字（移动端易溢出）——短码只出现在图例与洞察卡，足以摆脱"只靠颜色记忆"。

### 4. 网格色修复（暗色）

- 在 `@Composable` 作用域内先取色：网格线用 `MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)`，甜甜圈空环 / 排行轨道用 `MaterialTheme.colorScheme.surfaceVariant`。（实现说明：原计划用 `outlineVariant`，但本项目 `Theme.kt` 未显式设置该角色，会落到 M3 基线默认值、与中性盘不协调，故改用已显式定义的 `outline`/`surfaceVariant`。）
- 因 `DrawScope` 读不到 `MaterialTheme`，将取到的 `Color` 作为参数/闭包传入 `Canvas`。
- 替换全部 4 处 `Color(0xFFE7EAF0)`。

### 5. 配色精修（方案 B：和谐鲜明）

把 `LotColors.ChartPalette` 换为用户在可视化对比中选定的方案 B（冷暖相间、明度/饱和一致、玫红替纯红，兼顾区分度与不违和）：

```kotlin
val ChartPalette = listOf(
    Color(0xFF4F46E5), // M1 靛
    Color(0xFFF59E0B), // M2 琥珀
    Color(0xFF14B8A6), // M3 蓝绿
    Color(0xFFF43F5E), // M4 玫红
    Color(0xFF0EA5E9), // M5 天蓝
    Color(0xFFA855F7)  // M6 紫
)
```

- 已确认 `ChartPalette` 仅被统计页消费（`StatsScreen.kt:970` 的 `chartPalette()`），图例/各图表按索引取色，换列表内容即自动生效，无其他副作用。
- M2 琥珀与既有 `LotColors.Warning` 同值（#F59E0B），属不同语境（数据系列 vs 告警），沿用现状不处理。

## 测试

- `assembleDebug`、`testDebugUnitTest` 必须保持 `BUILD SUCCESSFUL`。
- 统计页无测试 tag（tag 全为 chat/welcome），17 个仪器化测试不受影响。
- 若抽出纯函数（如 `densityHint(modelCount, pointCount)` 返回提示文案，或短码生成 `modelTag(index)`），在 `app/src/test/.../feature/stats/` 加一个轻量 JVM 单测，沿用项目测试风格。

## 触及文件

- `app/src/main/java/com/example/iotgpt/feature/stats/ui/StatsScreen.kt`（主要；含新增 `ChartInsight` / `ChartInsightRow`）
- `app/src/main/java/com/example/iotgpt/ui/theme/LotDesignTokens.kt`（`LotColors.ChartPalette` 换为方案 B 6 色）
- 可能：`app/src/test/java/com/example/iotgpt/feature/stats/StatsInsightTest.kt`（如抽出纯函数）

## 风险

- Canvas 点击命中区与新短码/三态文案叠加后布局变化——通过本机构建 + 手动走查空/少/多三种数据规避。
- 改 `onInsight` 签名涉及 4 个图表 Composable，需同步改完，避免编译错误。
