# 聊天核心动效设计文档

- 日期：2026-06-18
- 范围：UI 审读文档「优先级 6：动效与反馈」中的"聊天核心动效包"（4 项），全部在 `feature/chat/ui/ChatScreen.kt`
- 状态：已与用户确认设计，待写实现

## 硬约束：流式输出必须原样保留

流式输出（增量文本逐字刷新）是原始基本要求，本次动效**不得改动其任何环节**：

- `ChatRepositoryImpl` 的 SSE 收集/节流落库循环、`OpenAiCompatibleClient.streamChatCompletion`：**完全不碰**。
- `MessageBubble` 中渲染流式文本的 `MessageContent(message.content)`（`ChatScreen.kt:2338/2341`）：**保留**。
- 本次只替换"生成中"的**视觉指示器**（`:2333` 的"正在请求模型回复..."文字、`:2344` 的 `LinearProgressIndicator`），不影响文本流式刷新。

## 目标（4 项动效）

1. 思考脉冲点 + 模型名，替掉流式态的直线进度条/等待文字。
2. 新消息 fade + slide-up 入场。
3. Claw 模式切换时输入栏/按钮色彩平滑过渡。
4. 待发附件卡片展开 / 收起。

全程 ≤300ms、不阻塞点击、复用 `LotMotion`（fast120/normal220/slow320），并提供 reduced-motion 降级。

## 非目标（YAGNI / 本次不做）

- 会话切换 crossfade、引导翻页过渡（属"核心包 + 页面过渡"，本次不做）。
- 不改 repository / SSE / ViewModel 逻辑。
- 不改任何 `AppTestTags` 节点结构，确保 17 个仪器化测试不受影响。
- 不新增依赖。

## 设计细节

### ① 思考脉冲点 + 模型名

新增 `ThinkingIndicator(modelLabel: String, reduceMotion: Boolean)`：

- 3 个小圆点（`primary` 色），用 `rememberInfiniteTransition` + 每点 `infiniteRepeatable(tween(600, easing=FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i*160))` 做错峰 alpha 跳动；右侧 `Text("$modelLabel · 思考中")`（`onSurfaceVariant`, labelMedium）。
- `reduceMotion` 为真时：圆点静止为固定 alpha，不建 infinite transition。

`MessageBubble` 改动：

- 增加参数 `modelLabel: String`、`reduceMotion: Boolean`。
- 流式且 `content.isBlank()`：原 "正在请求模型回复..." → `ThinkingIndicator(modelLabel, reduceMotion)`。
- 流式且有内容：保留 `MessageContent(content)`，其后原 `LinearProgressIndicator` → 一行小 `ThinkingIndicator`（仅点，或点+模型名）。
- 调用点 `ChatScreen.kt:892` 传入 `modelLabel = uiState.activeModelProfile?.model ?: "模型"` 与 `reduceMotion`。

### ② 新消息 fade + slide-up 入场

在 `items(visibleMessages.asReversed(), key={it.id})` 的气泡根 `Modifier` 上：

- `if (reduceMotion) Modifier else Modifier.animateItem(fadeInSpec = tween(LotMotion.normal))`。
- 新气泡淡入；列表其余项由 `animateItem` 的 placement 动画平滑位移，产生"上滑"观感。与现有 `reverseLayout` 和 `followLatest` 跟随滚动兼容。

### ③ Claw 模式色彩过渡

在 ChatScreen 主 composable（Composer 区域作用域内）：

- `val accent by animateColorAsState(targetValue = if (uiState.isClawMode) LotColors.Claw else MaterialTheme.colorScheme.primary, animationSpec = tween(if (reduceMotion) 0 else LotMotion.normal), label = "claw-accent")`。
- 输入框聚焦/未聚焦边框、光标用 `accent` 派生；发送/执行按钮 `containerColor` 用 `accent`。切模式时平滑过渡而非瞬切。
- 保留按钮上的 `contentDescription` 与 `testTag`。

### ④ 待发附件展开 / 收起

- `pendingAttachment` 卡片改为 `AnimatedVisibility(visible = pendingAttachment != null, enter = expandVertically(tween(LotMotion.normal)) + fadeIn(...), exit = shrinkVertically(tween(LotMotion.fast)) + fadeOut(...))`；内容用 `remember` 的 last-value 渲染以保证退场动画有内容（同统计页洞察卡写法）。
- `reduceMotion` 为真时直接条件渲染（无 AnimatedVisibility），瞬时显隐。

### Reduced-motion 工具

```kotlin
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
```

放 `ChatScreen.kt`；新增 `import android.provider.Settings`。

## 测试

- `assembleDebug` + `testDebugUnitTest` 保持 `BUILD SUCCESSFUL`。
- 纯 Compose 视觉，无可单测的纯逻辑，不新增单测。
- 保留全部 `AppTestTags` 节点，不破坏仪器化测试；注意：`ThinkingIndicator` 的 infinite 动画仅在 `isStreaming` 时存在，测试用例不触发真实流式（无 API Key），故不会令 `waitForIdle` 挂起。
- 动效观感需在模拟器现场确认（含浅/深色与系统关动画）。

## 触及文件

- `app/src/main/java/com/example/iotgpt/feature/chat/ui/ChatScreen.kt`（唯一）
