# AIoT 智能助手 App 完整版开发提示词

> **实现状态说明（2026-06-17）**：本文档不是原始大作业要求，而是其他 AI 工具基于题目扩写出的开发提示词，历史文字中的项目名、课堂语境和阶段划分仅作为开发过程记录。真实原始要求已单独整理到 `原始大作业要求.md`。当前 App 对外名称为 `lot`，定位为面向物联网专业用户的 Android AI 助理；实际项目已完成真实 OpenAI-compatible SSE 流式输出（非打字机模拟）、多模型配置、Claw 本地智能体、多模态采集、系统通知等能力。具体实现细节和验证结果请参阅 `README.md`、`答辩说明.md` 和 `最终测试报告.md`。

## 总目标

请你作为资深 Android 工程师，基于当前 Android Studio 空项目和已有初始代码，完整开发一个课程大作业 App：AI 物联网专业智能助手，项目名暂定为 `IoT-GPT` 或 `AIoT Assistant`。

这个 App 面向物联网专业学生、教师和工程实践场景，核心能力包括大模型对话、多模态数据采集、会话持久化、统计监测、模型配置、系统通知、本地智能体任务演示。最终作品需要能真实调用大模型 API，并且适合课堂答辩演示。

语言可以 Kotlin 和 Java 混用，但优先使用 Kotlin。必须保证项目能在 Android Studio 中正常编译运行。

## 真实 API 要求

必须接入真实大模型 API。优先采用 OpenAI 兼容接口，方便接入 DeepSeek、通义千问、Kimi、小米 MiMo、OpenRouter 或其他兼容服务。

请实现通用 OpenAI Compatible Client：

- 支持配置 `baseUrl`
- 支持配置 `apiKey`
- 支持配置 `model`
- 支持普通 Chat Completions 请求
- 优先支持流式输出，如果实现困难，可先实现非流式请求，再预留流式接口（**注：实际项目已实现真实 SSE 流式输出**）
- API Key 不要硬编码在源码里，必须通过设置页输入并使用 DataStore 保存
- 如果 API 请求失败，要给出清晰错误提示
- 设置页需要提供“测试连接”按钮，用一个简单 prompt 验证 API 是否可用

请求格式参考 OpenAI 兼容接口：

```json
{
  "model": "用户选择的模型名",
  "messages": [
    {"role": "system", "content": "你是 AI 物联网专业智能助手..."},
    {"role": "user", "content": "用户输入"}
  ],
  "temperature": 0.7,
  "stream": true
}
```

> **注**：以上为简化参考格式。实际项目使用 `stream: true`，通过 SSE 分片接收并增量更新 UI。

系统提示词要求突出物联网专业能力：

```text
你是一个 AI 物联网专业智能助手，擅长解释传感器、嵌入式开发、MQTT、HTTP、CoAP、Modbus、边缘计算、设备联网、数据采集、故障诊断和课程学习问题。回答要清晰、准确、适合学生理解；涉及代码时给出简洁示例；涉及设备操作时提醒安全和权限。
```

## 技术栈

优先采用：

- Kotlin
- Jetpack Compose
- Material 3
- MVVM
- Hilt
- Room
- DataStore
- Retrofit2 或 OkHttp3
- Kotlin Coroutines + Flow
- Navigation Compose
- Coil
- NotificationCompat + NotificationChannel
- WorkManager 或 Foreground Service

图表库优先使用 Vico 或 MPAndroidChart。如果依赖冲突或接入成本过高，可以使用 Compose Canvas 自绘简单柱状图、折线图或环形图。

文档解析优先支持 txt、pdf、docx。doc 可作为可选增强项。如果 Android 端接入 Apache POI / PdfBox 体积或兼容性不佳，可先实现文件选择、文件元信息展示、txt 文本读取、pdf/docx 预留解析接口，并在答辩文档中说明。

## 项目结构要求

采用 feature-based package structure，便于答辩定位代码：

```text
com.iotgpt.app/
  MainActivity.kt
  IoTGptApplication.kt
  navigation/
    AppNavigation.kt
    Routes.kt
  core/
    theme/
    components/
    network/
      OpenAiCompatibleClient.kt
      LlmApiService.kt
      LlmModels.kt
      NetworkMonitor.kt
    database/
      AppDatabase.kt
      entity/
      dao/
    preferences/
      SettingsStore.kt
    notification/
      NotificationHelper.kt
    permission/
      PermissionManager.kt
    util/
      TimeUtils.kt
      FileUtils.kt
  feature/
    chat/
      ui/
      ChatViewModel.kt
      ChatRepository.kt
      ChatRepositoryImpl.kt
    history/
      ui/
      HistoryViewModel.kt
    stats/
      ui/
      StatsViewModel.kt
    settings/
      ui/
      SettingsViewModel.kt
    welcome/
      ui/
      WelcomeViewModel.kt
    agent/
      ui/
      AgentViewModel.kt
      ClawAgentController.kt
```

如果初始项目已有包名或结构，请尊重现有结构，在不破坏项目的前提下调整。

## 数据库设计

使用 Room 保存会话、消息、模型使用统计、智能体任务记录。

### ConversationEntity

- `id: String`
- `title: String`
- `summary: String?`
- `createdAt: Long`
- `updatedAt: Long`
- `modelId: String`
- `messageCount: Int`

### MessageEntity

- `id: String`
- `conversationId: String`
- `role: String`，取值为 `system`、`user`、`assistant`
- `content: String`
- `attachmentJson: String?`
- `createdAt: Long`
- `isStreaming: Boolean`
- `tokenCount: Int?`

### ModelUsageEntity

- `id: String`
- `modelId: String`
- `conversationId: String?`
- `promptTokens: Int`
- `completionTokens: Int`
- `totalTokens: Int`
- `createdAt: Long`

如果 API 不返回 token，用字符数估算，并在代码和答辩文档中说明。

### AgentTaskEntity

- `id: String`
- `type: String`，如 `sms`、`camera`、`download`
- `status: String`
- `description: String`
- `createdAt: Long`
- `completedAt: Long?`

## 功能模块 1：对话窗口

实现类似 DeepSeek / ChatGPT 的移动端对话体验。

必须包含：

- 无限滚动对话窗口
- 用户消息和 AI 消息气泡
- 异步 AI 响应
- AI 回复 loading 动效
- 会话持久化保存
- 历史会话列表
- 每个会话自动生成并保存摘要
- 智能体会话异步加载
- 新建会话
- 删除会话
- 切换会话
- 当前模型标签显示
- Markdown 基础渲染，至少支持换行、列表、代码块的可读展示

UI 要求：

- 顶部 TopAppBar：左侧打开历史会话抽屉，中间显示会话标题，右侧新建会话按钮
- 历史会话抽屉样式参考 DeepSeek：搜索框、会话标题、摘要、时间、当前会话高亮
- 中间 LazyColumn 展示消息
- 用户消息右对齐，AI 消息左对齐
- 底部输入栏包含文本输入、发送按钮、附件按钮
- 附件按钮点击后弹出底部面板：拍照、选择文档、录音

真实 API 逻辑：

- 用户发送消息后立即插入 Room
- ViewModel 调用 Repository
- Repository 调用 OpenAI 兼容 API
- API 返回后插入 assistant 消息
- 请求期间 UI 显示 loading
- 请求失败时插入错误状态或显示 Snackbar
- 后台收到 AI 消息时触发系统通知

会话摘要逻辑：

- 每个会话至少有一个自动摘要
- 当消息数达到 4 条后，调用大模型生成一句话摘要
- 之后每增加 6 到 10 条消息更新一次摘要
- 摘要保存到 `ConversationEntity.summary`
- 如果摘要 API 失败，使用本地规则降级生成摘要，例如截取首条用户消息

## 功能模块 2：多模态数据上传

必须支持在会话中调用系统服务采集数据：

- 拍照
- 文档选择，支持常见文件，如 doc、docx、pdf、txt
- 录音

实现要求：

- 使用系统相机 Intent 或 CameraX 拍照
- 使用系统文件选择器选择文档
- 使用 MediaRecorder 录音
- 采集结果以附件卡片形式展示在聊天窗口
- 附件信息保存到 MessageEntity 的 `attachmentJson`
- 对 txt 文件尽量读取文本并作为上下文发送给大模型
- 对图片、pdf、docx 如果暂时无法完整解析，也要展示文件名、大小、Uri，并把说明文本发送给模型
- 文件大小限制建议 10MB
- 所有权限必须运行时申请

如果接入的模型支持图片输入，可以预留 vision 请求结构；如果模型暂不支持，则将图片作为附件记录和演示素材。

## 功能模块 3：通知

必须实现：

- App 内 Snackbar / Banner 通知
- Android 系统消息栏通知
- 创建 NotificationChannel
- Android 13+ 申请 POST_NOTIFICATIONS 权限
- 点击通知跳转到对应会话

触发场景：

- AI 回复完成
- 下载任务完成
- Claw 模式任务完成

## 功能模块 4：统计与监测页面

实现 Dashboard / StatsScreen。

必须展示：

- 总会话数
- 总消息数
- 今日消息数
- 用户消息数
- AI 回复数
- 智能体数量统计
- 各模型调用次数
- token 或字符估算使用量
- 当前网络状态
- 当前 API 服务可用性
- 最近一次模型请求时间

UI 布局建议：

- 顶部：数据中心标题和网络状态胶囊标签
- 第一行：总会话数、总消息数、今日消息数
- 第二块：最近 7 天消息趋势
- 第三块：模型使用统计
- 第四块：智能体状态
- 第五块：网络状态详情

视觉风格：

- 简洁、专业、偏工程工具风
- 不要做成营销落地页
- 卡片圆角不超过 12dp
- 颜色以蓝、绿、中性灰为主，避免整页过度渐变

## 功能模块 5：设置与配置

SettingsScreen 必须包含：

- API Base URL 输入框
- API Key 输入框，支持显示/隐藏
- 模型名输入或下拉选择
- 测试 API 连接按钮
- 当前模型切换
- 深色模式、浅色模式、跟随系统
- 清空历史会话
- 导出调试信息
- App 版本和项目说明

配置保存：

- 使用 DataStore
- API Key 不写入源码
- UI 中避免直接明文长时间暴露 API Key

预置模型配置：

- DeepSeek OpenAI Compatible
- OpenAI Compatible Custom
- 通义千问兼容接口
- 小米 MiMo 兼容接口
- 自定义模型

## 功能模块 6：Welcome Page 与引导动效

必须包含：

- 首次启动 Welcome Page
- 首次加载引导页
- 会话加载动效
- AI 思考中动效
- 空状态引导

设计建议：

- App 首屏不做营销页，Welcome 只用于首次启动
- 主界面第一屏直接进入对话
- 引导页 3 页：
  1. AI 物联网问答
  2. 多模态采集
  3. 数据统计与模型配置
- 引导完成状态用 DataStore 保存

## 功能模块 7：Claw 模式本地智能体

这是附加分模块，必须安全、可控、适合课堂演示。

实现独立 AgentScreen，展示：

- 智能体列表
- 任务状态
- 任务日志
- 执行按钮

支持任务：

### 自动发送短信

- 使用 `SEND_SMS` 权限
- 必须用户手动输入或选择测试手机号
- 执行前弹出二次确认框
- 发送结果写入任务日志
- 不允许后台隐蔽发送

### 自动拍照

- 使用相机权限
- 用户点击执行后调用相机
- 拍照后显示照片预览
- 结果写入任务日志

### 自动下载服务

- 用户输入 URL
- 使用 WorkManager 或 Foreground Service 下载
- 显示进度
- 下载完成触发通知
- 结果写入任务日志

## 权限要求

在 AndroidManifest.xml 中按需声明，并在运行时动态申请：

- INTERNET
- CAMERA
- RECORD_AUDIO
- POST_NOTIFICATIONS
- SEND_SMS
- FOREGROUND_SERVICE
- READ_MEDIA_IMAGES 或系统文件选择器相关权限

权限申请 UI 要解释用途，权限拒绝时不能崩溃。

## 答辩友好要求

请生成或维护：

- `README.md`
- `答辩说明.md`

`答辩说明.md` 必须包含：

- 项目简介
- 功能模块总览
- 技术栈
- 架构图或文字说明
- 数据库表说明
- API 调用流程
- 每个功能对应的关键代码文件
- 演示步骤
- 可能被老师问的问题与回答
- 哪些功能是真实实现
- 哪些功能是预留或降级实现

代码注释要求：

- 每个核心 `.kt` 文件顶部说明职责
- 核心函数使用简短 KDoc
- 复杂逻辑写必要注释
- 不要给简单代码写废话注释
- 可在关键演示点添加 `// 答辩演示点：...`

## UI 设计细节

整体风格：

- Material 3
- 专业、清爽、偏工程工具
- 不要做花哨营销页
- 主色建议：科技蓝 `#2563EB`，物联网绿 `#10B981`，中性灰
- 深色模式要可用
- 页面不要过度堆叠卡片

主导航：

- 底部导航 4 个 Tab：
  - 对话
  - 统计
  - 智能体
  - 设置

对话页：

- 历史会话使用 ModalNavigationDrawer
- 消息气泡最大宽度约 78%
- AI 回复支持代码块可读展示
- 输入栏固定底部
- 附件以小卡片展示

统计页：

- 使用可滚动单页
- 顶部总览数字醒目
- 图表简单但清晰
- 网络状态使用图标和文字说明

设置页：

- 按分组列表展示
- API Key 输入框带显示/隐藏按钮
- 模型选择清晰
- 危险操作如清空历史必须二次确认

智能体页：

- 展示 3 个智能体任务卡片
- 每个卡片包含状态、权限提示、执行按钮、最近日志

## 错误处理

必须处理：

- 无网络
- API Key 为空
- API Key 无效
- API 请求超时
- 模型返回格式异常
- 文件选择失败
- 文件过大
- 权限被拒绝
- 数据库异常

不要让 App 因上述情况崩溃。

## 开发节奏

请分阶段开发，每个阶段结束后必须输出：

- 完成内容
- 修改文件
- 如何运行
- 如何测试
- 是否有未完成项
- 下一阶段计划

阶段划分：

1. 检查当前项目结构，确认 Gradle、包名、Compose 配置，建立基础架构。
2. 实现主题、导航、四个主页面骨架。
3. 接入 Room，建立会话、消息、模型统计、智能体任务表。
4. 实现 ChatScreen、本地会话保存、历史会话抽屉。
5. 接入 OpenAI 兼容真实 API，实现普通对话请求。
6. 实现 AI loading、错误处理、摘要生成、模型使用统计。
7. 实现拍照、文件选择、录音、多模态附件卡片。
8. 实现系统通知和 App 内通知。
9. 实现统计与监测页面。
10. 实现设置页、API Key、Base URL、模型切换、主题切换。
11. 实现 Welcome Page、Onboarding、加载动效。
12. 实现 Claw 模式：短信、拍照、下载服务。
13. 补充 README、答辩说明、最终测试与修复。

## 第一阶段执行提示词

请先执行第 1 阶段：检查当前 Android Studio 项目结构并建立基础架构。

具体要求：

1. 先阅读当前项目文件，不要盲目覆盖已有代码。
2. 判断项目是否已经启用 Kotlin、Compose、Material 3。
3. 如果缺少必要依赖，请修改 Gradle 配置。
4. 建立基础包结构：
   - core
   - navigation
   - feature/chat
   - feature/stats
   - feature/agent
   - feature/settings
   - feature/welcome
5. 实现 MainActivity 中的 Compose 入口。
6. 实现底部导航，包含对话、统计、智能体、设置四个页面。
7. 每个页面先做可运行 UI 骨架。
8. 保证项目能编译。
9. 不要在第一阶段接入数据库和真实 API。

完成后请输出：

- 当前项目结构判断
- 修改了哪些文件
- 新增了哪些依赖
- 如何运行
- 下一阶段建议

## 扩展与完善阶段总目标

在第 1 至第 13 阶段完成的可运行版本基础上，继续提升项目的真实可用性、答辩亮点和长期可维护性。扩展开发必须遵守以下原则：

1. 不破坏已完成的聊天、统计、设置、多模态、通知和 Claw 模式功能。
2. 所有 API Key 仍然只能由用户在 App 内输入并保存在本机 DataStore，不允许硬编码。
3. 每个扩展阶段开始前先审查相关已有代码，结束后必须执行构建、单元测试和敏感信息扫描。
4. UI 继续保持 Material 3、工程工具风、清爽克制，不做营销页。
5. 新功能必须有降级说明；依赖真实 API、真机权限或外部网络的部分要在 README 和答辩说明中写明。

扩展阶段划分：

14. 多模型配置管理：设置页支持保存多套第三方模型配置，聊天页支持快速切换当前模型。
15. 聊天体验完善：增加消息复制、重新生成、代码块可读优化、错误重试入口和更清晰的空状态。
16. 多模态请求升级：在现有附件基础上，为支持 vision 的 OpenAI 兼容模型预留/实现图片消息结构；不支持时保留文本降级。
17. 统计与调试增强：增加模型配置状态、最近失败原因、导出更完整的调试报告，并优化统计页可读性。
18. 文档与最终回归：更新 README、答辩说明、最终测试报告，完成全量构建、测试、扫描和人工验证清单。

## 第十四阶段执行提示词

请执行第 14 阶段：实现多模型配置管理和聊天页快速切换。

具体要求：

1. 先审查 `SettingsStore`、`SettingsViewModel`、`SettingsScreen`、`ChatViewModel`、`ChatScreen`、`ChatRepositoryImpl`，确认当前单模型配置流转。
2. 在 DataStore 中新增模型配置列表，至少包含：
   - `id: String`
   - `name: String`
   - `baseUrl: String`
   - `apiKey: String`
   - `model: String`
   - `provider: String`
3. 兼容旧字段：如果用户已有单组 Base URL/API Key/model，迁移为默认配置。
4. 设置页支持：
   - 查看当前已保存配置列表
   - 新增或更新一套模型配置
   - 选择某套配置为当前聊天模型
   - 删除非当前配置
   - 套用 DeepSeek、通义、MiMo、OpenAI、自定义预设
5. 聊天页顶部或输入区附近支持快速切换当前模型配置，显示配置名和模型名。
6. Repository 调用 API 时必须读取当前激活配置；模型使用统计继续按模型名记录。
7. 清晰处理无 API Key、无配置、配置为空、删除当前配置等边界情况。
8. 更新 README、答辩说明和最终测试报告中的模型配置说明。
9. 执行：
   - `assembleDebug`
   - `testDebugUnitTest`
   - 敏感信息和 TODO/FIXME 扫描

完成后请输出：

- 审查结论
- 新增/修改的文件
- 多模型配置使用方法
- 验证结果
- 下一阶段建议

## 第十五阶段执行提示词

请执行第 15 阶段：完善聊天体验。

具体要求：

1. 支持长按或按钮复制消息文本。
2. 支持对最后一条用户消息重新生成 AI 回复。
3. 优化 Markdown/代码块显示，至少让列表、换行和代码段在气泡内可读。
4. API 请求失败的 assistant 消息提供重试入口。
5. 空会话状态提供物联网学习问题示例。
6. 保证历史会话、附件发送和通知逻辑不回退。
7. 更新 README、答辩说明和测试报告。
8. 完成构建、测试和扫描。

## 第十六阶段执行提示词

请执行第 16 阶段：升级多模态请求能力。

具体要求：

1. 审查现有附件 JSON、FileUtils、ChatRepositoryImpl 和 OpenAiCompatibleClient。
2. 为图片附件增加 OpenAI 兼容 vision content 结构支持；如果当前模型或服务不支持 vision，自动降级为文本说明。
3. txt 文件继续读取文本并进入上下文。
4. pdf/docx 暂不能完整解析时，要保留文件元信息和清晰说明。
5. 设置页或模型配置中预留“支持图片输入”开关。
6. 更新文档和测试报告。
7. 完成构建、测试和扫描。

## 第十七阶段执行提示词

请执行第 17 阶段：统计与调试增强。

具体要求：

1. 统计页展示当前激活模型配置名、模型名、Base URL 域名和 API Key 是否已配置。
2. 记录并展示最近一次 API 失败原因。
3. 调试信息导出包含模型配置列表摘要，但不能导出 API Key 明文。
4. 优化统计页中模型使用统计和任务统计的可读性。
5. 更新文档和测试报告。
6. 完成构建、测试和扫描。

## 第十八阶段执行提示词

请执行第 18 阶段：扩展功能最终回归与文档收口。

具体要求：

1. 全面回顾第 14 至第 17 阶段是否达成。
2. 更新 README、答辩说明、最终测试报告。
3. 确认没有硬编码 API Key、TODO/FIXME、阶段占位文案。
4. 执行最终 `assembleDebug` 和 `testDebugUnitTest`。
5. 输出最终交付说明、APK 路径、真实 API/真机验证注意事项。
