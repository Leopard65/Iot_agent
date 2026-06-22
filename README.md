# lot

lot 是一个面向普通用户的 Android AI 智能助手。项目使用 Kotlin、Jetpack Compose、Room、DataStore、OkHttp 和 WorkManager 实现，支持 OpenAI 兼容大模型接口、本地会话保存、多模态附件、统计监测、通知和 Claw 本地智能体任务。

项目对应的大作业题目为“AI物联网专业智能助手APP”。物联网是作业题目背景，不是 App 的产品边界；当前 App 对外定位为通用 AI 智能助手。原始评分要求已整理在 `原始大作业要求.md`。

下一阶段 UI/UX 美化建议见 `UI_UX_下一阶段设计审读.md`。

## 2026-06-18 进度记录

- 产品定位已收口为“通用 AI 智能助手”，物联网仅作为作业题目背景，不作为 App 产品边界。
- 底层 system prompt、欢迎页、聊天空状态、统计洞察、设置页版本标识和文档说明均已去除物联网专用定位。
- 新增 `LotDesignTokens.kt`，并将聊天、统计、设置等页面逐步迁移到统一 spacing、radius、motion 和语义色。
- 聊天页已加入通用 Prompt Deck、多行输入 Composer、AI/Claw 模式控制、能力条和 DeepSeek 风格历史抽屉分组。
- `Claw` 已收敛为聊天页内的本地模式开关，并通过历史抽屉与统计页展示任务日志。
- 已通过 `assembleDebug`、`assembleRelease`、`testDebugUnitTest` 和 `connectedDebugAndroidTest`，模拟器 `Medium_Phone(AVD) - 12` 上 44 个仪器化测试通过。

## 功能概览

- 启动与 Onboarding：冷启动使用 Android SplashScreen API，首次启动进入 3 页功能引导。
- AI 聊天：支持多轮会话、历史会话抽屉、新建/切换/删除会话。
- 流式输出：聊天页使用 OpenAI 兼容 SSE 分片更新回复，边生成边写入本地会话。
- 聊天体验增强：支持消息复制、失败回复重试、最后一条用户问题重新生成、代码块可读展示和空状态示例问题。
- OpenAI 兼容 API：可保存多套 Base URL、API Key、模型名称配置，支持 DeepSeek、通义、MiMo、OpenAI 与自定义服务，并可在聊天页快速切换。
- 本地存储：Room 保存会话、消息、模型调用统计和智能体任务日志。
- 多模态附件：支持拍照、文件选择、录音，文本附件会加入模型上下文。
- Vision 请求预留：模型配置可开启“支持图片输入”，图片附件会以 OpenAI 兼容 `image_url` content part 发送；未开启或图片过大时自动文本降级。
- 统计监测：展示会话数、消息数、当前模型配置、模型调用、Token/字符估算、最近 API 失败原因、网络/API 状态和 7 日趋势。
- 通知：AI 回复完成、Claw 任务完成后触发系统通知，并提供 App 内状态提示。
- 设置：保存 API 配置、切换主题、清空历史、导出调试信息。
- Claw 智能体：提供短信、拍照、下载服务和 App-Leap 本地联动能力。

## 技术栈

- Kotlin 2.0.21
- Android Gradle Plugin 8.11.2
- Jetpack Compose + Material 3
- Navigation Compose
- Room 2.7.0
- DataStore Preferences 1.1.1
- OkHttp 4.12.0
- WorkManager 2.9.1
- minSdk 26，targetSdk 36，compileSdk 36

## 项目结构

```text
app/src/main/java/com/example/iotgpt
├── MainActivity.kt
├── navigation/              # 底部导航与页面路由
├── ui/theme/                # Material 3 主题
├── core/
│   ├── components/          # 通用 Compose 页面组件
│   ├── database/            # Room Entity、DAO、Database
│   ├── network/             # OpenAI 兼容 API 客户端
│   ├── notification/        # 系统通知封装
│   ├── preferences/         # DataStore 设置
│   ├── util/                # 文件、拍照、录音工具
│   └── worker/              # WorkManager 下载任务
└── feature/
    ├── welcome/             # 欢迎页
    ├── chat/                # AI 对话与本地会话
    ├── stats/               # 统计监测
    ├── agent/               # Claw 本地智能体任务
    └── settings/            # API、主题、维护设置
```

## 启动与引导

App 冷启动使用 Android SplashScreen API，避免固定延时阻塞主流程。首次启动时继续展示 3 页 Welcome/Onboarding，用于说明 AI 对话、多模态采集、统计监测和模型配置能力。

## 运行方式

1. 使用 Android Studio 打开项目根目录。
2. 等待 Gradle Sync 完成。
3. 连接 Android 设备或启动模拟器。
4. 点击 Run，或在终端执行：

```powershell
.\gradlew.bat "-Dhttp.proxyHost=" "-Dhttps.proxyHost=" "-Dhttp.proxyPort=" "-Dhttps.proxyPort=" assembleDebug
```

当前调试 APK 生成路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## API 与模型配置

首次进入主界面后打开“设置”页：

1. 在“模型配置列表”中查看当前已保存的配置。
2. 选择预置模型，或填写自定义配置名称、服务商、Base URL 和模型名称。
3. 输入对应服务的 API Key。
4. 点击“保存并设为当前”。
5. 点击“测试连接”，确认服务可用。
6. 回到聊天页，可在顶部模型切换条中快速切换当前配置。

API Key 只通过 DataStore 保存在本机，没有写入源码。没有真实 Key 时，聊天页会显示明确的 API 配置错误。旧版本保存过的单组 Base URL/API Key/model 会自动作为默认模型配置读取。

常用配置示例：

| 服务 | Base URL | 模型 |
| --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | `deepseek-chat` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode` | `qwen-plus` |
| MiMo | `https://api.xiaomimimo.com/v1` | `mimo-v2-pro` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4.1-mini` |

每套模型配置包含配置名称、服务商、Base URL、API Key 和模型名称。删除配置时不能删除当前激活配置，需要先切换到其他配置。

如果所选服务和模型支持图片理解，可在模型配置中开启“支持图片输入”。开启后，图片附件会在请求前转换为 data URL，并按 OpenAI 兼容的 `image_url` 结构发送。为避免请求体过大，超过 4MB 或读取失败的图片会自动降级为附件说明文本。

## 聊天增强操作

- 点击消息气泡中的“复制”可复制消息正文。
- 当 AI 回复为 API 或模型配置错误时，气泡内会显示“重试”入口。
- 输入区右侧的“重新生成”会基于最后一条用户消息重新请求 AI 回复。
- 空会话会提供写作润色、学习解释、资料总结、计划生成等通用示例问题。
- 使用 Markdown 代码围栏的内容会以等宽字体代码块展示。

## 统计与调试

统计页会展示当前激活模型配置、模型名、Base URL 域名、API Key 是否已配置、图片输入开关状态和最近一次 API 失败原因。设置页的“导出调试信息”会导出模型配置摘要和最近 API 错误，但不会导出 API Key 明文。

## 权限说明

应用声明的权限用于 AI 助理的联网、多模态采集和本地任务能力：

- `INTERNET`：请求大模型 API 和下载文件。
- `ACCESS_NETWORK_STATE`：统计页展示网络状态。
- `CAMERA`：拍照附件和 Claw 拍照任务。
- `RECORD_AUDIO`：录音附件。
- `POST_NOTIFICATIONS`：AI 回复和任务完成通知。
- `SEND_SMS`：Claw 短信任务，执行前需要运行时权限和二次确认。
- `FOREGROUND_SERVICE`：为后续长期任务扩展预留。

## Claw 模式安全约束

Claw 模式不是静默后台控制，而是需要用户确认的本地能力：

- 短信必须用户手动填写手机号和内容。
- 短信发送前会请求 `SEND_SMS` 权限，并弹出确认对话框。
- 拍照由用户点击按钮后调用系统相机。
- 下载由用户填写 URL 后通过 WorkManager 执行，并记录进度与任务日志。

## 测试

本机最终验证命令：

```powershell
.\gradlew.bat "-Dhttp.proxyHost=" "-Dhttps.proxyHost=" "-Dhttp.proxyPort=" "-Dhttps.proxyPort=" assembleDebug
.\gradlew.bat "-Dhttp.proxyHost=" "-Dhttps.proxyHost=" "-Dhttp.proxyPort=" "-Dhttps.proxyPort=" testDebugUnitTest
rg -n "TODO|FIXME|Bearer sk-|sk-[A-Za-z0-9]|阶段 1|后续阶段" app\src\main\java app\src\main\AndroidManifest.xml app\build.gradle.kts gradle\libs.versions.toml
```

验证结论记录在 `最终测试报告.md`。

## 最终回归结论

第 1 至第 18 阶段均已完成。项目已完成最终构建、单元测试和源码/配置敏感信息扫描。真实 AI 回复、图片理解、短信、通知、拍照和下载等能力仍需要真实 API Key、支持 vision 的模型、真机权限和可用网络环境进行现场验证。

## 已知说明

- 真实大模型请求需要在设置页填入可用 API Key。
- 短信功能建议使用有 SIM 卡且允许短信发送的真机测试；模拟器通常不能完整验证短信发送。
- 拍照功能依赖系统相机 App。
- 下载功能需要输入可公网访问的文件 URL。
