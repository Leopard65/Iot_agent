# 多模态与 Claw 完善计划

## 阶段计划

1. 梳理现有多模态链路：确认拍照、文档、录音入口，确认附件 JSON、Room 消息、模型上下文和 vision 请求结构。
2. 安全补齐：聊天页 Claw 短信必须在权限之外再做二次确认，取消和拒绝都写入会话。
3. 指令式采集：在 Claw 本地模式中支持通过会话指令触发拍照、文档选择和录音采集。
4. 图片增强：图片发送给支持 vision 的模型前，先尝试压缩到 4MB 内；失败后再降级为附件说明文本。
5. 文档和音频提示：文档提取失败、音频未转写等情况必须给出明确提示，不让用户误以为模型已经读取完整内容。
6. 回归验证：运行构建、单元测试和敏感信息/占位扫描；每次测试发现的不足写入后续待做。

## 本轮已完成

- 聊天页 Claw 短信增加二次确认，取消确认会写入会话。
- Claw 本地模式新增“选择文档/上传文件/文档/file/document”等指令，触发 Android 系统文件选择器。
- Claw 本地模式新增“录音/音频/语音/麦克风/record/audio/voice”等指令，触发录音采集流程。
- 文档和录音由指令采集后，会放入待发送附件区，并写入 Claw 本地结果消息。
- Vision 图片发送前增加压缩逻辑，压缩后仍超过限制才降级。
- Claw 指令解析已抽离为可单测模块，覆盖拍照、文档、录音、下载、短信和系统跳转。
- 关键聊天控件已增加稳定测试标签，可供 Compose UI 测试和模拟器回归使用。
- 已新增模拟器 Compose 仪器测试，覆盖首次启动/引导进入聊天页、开启 Claw 模式、执行本地指令并写入会话结果。
- 已新增短信安全确认模拟器测试，覆盖已授予短信权限后仍必须弹出 App 内二次确认，取消后写入会话结果。
- 已新增短信权限拒绝、多模态菜单和录音面板模拟器测试，覆盖系统权限拒绝后的会话回写，以及附件入口的基础可用性。
- 已新增录音开始/结束生成待发送音频附件、系统文件选择器取消返回 App 的模拟器测试。
- 已新增系统文件选择器实际选中文本文档并生成待发送文档附件的模拟器测试。
- 已新增仓储级附件链路模拟器测试，使用 fake LLM 服务验证文档正文、录音限制提示、非 vision 图片降级和 vision 图片 data URL 会进入模型请求上下文。
- 已新增 OpenAI-compatible 网络客户端 JVM 单测，使用 MockWebServer 验证文本请求、多模态图片 `content` 数组、URL 拼接、鉴权头、usage 解析和错误消息。
- 仓储级附件链路测试已改为独立测试 DataStore 文件，避免污染或依赖目标 App 的真实设置状态。
- Room 编译器已从 Kapt 迁移到 KSP，消除了 Kotlin 2.0 下 Kapt 回退到 1.9 的构建提示。
- 修复 Claw “打开 QQ/打开某应用”在真机上回退为浏览器搜索的问题：补充 Android 包可见性 `<queries>`，明确打开应用失败时写入失败原因，不再静默降级为搜索。
- 聊天页去掉底部导航中的独立 Claw 页，只保留对话内 Claw 开关；压缩 Claw 输入区和顶部工具区，减少每个主导航页顶部留白。
- 设置页改为以用户自建模型配置为中心：主列表只展示配置名、供应商、模型名和状态，Base URL/API Key/能力开关集中放到新增或编辑弹窗内。
- 模型切换改为简洁下拉，只展示已保存配置中的模型名；新增“复制当前”入口，方便同一供应商下保存多个不同模型。
- 模型配置新增“支持思考模式”和“默认开启思考”字段；聊天页对支持思考的当前模型显示开关，开关状态会持久化到当前模型配置。
- OpenAI-compatible 请求体在思考模式开启时会实际携带 `enable_thinking=true` 和 `thinking.type=enabled`，并新增单测锁定该行为。
- 备份与迁移规则显式包含 Room database、DataStore 和 shared preferences，并开启跨版本恢复，降低重新安装或设备迁移时丢失聊天记录/API 配置的风险。
- 接入 OpenAI-compatible 语音转写：模型配置可开启语音转写并填写转写模型，录音附件会先调用 `/v1/audio/transcriptions`，再把转写文本加入聊天上下文。
- 录音转写成功后会把文本缓存回附件 JSON，重新生成回复时复用缓存，避免重复转写。
- 新增录音附件 UI 端到端仪器测试：真实录音控件生成附件，连接本地 MockWebServer 完成 mock 转写和 mock 聊天响应。
- App-Leap 扩展拨号、地图导航、应用市场搜索/详情等系统级动作，并补充对应包可见性声明和单元测试。
- 新增 `.gitattributes` 明确 Kotlin、XML、Markdown、Gradle 等文本文件行尾策略，减少 Windows 下 LF/CRLF 噪音。
- 允许 cleartext HTTP，便于 Android 真机连接本地 OpenAI-compatible 服务或仪器测试 MockWebServer；真实线上服务仍建议使用 HTTPS。

## 后续待做

- 继续增加 UI 自动化或仪器测试，覆盖真实拍照结果，以及系统文件选择器选中文档后通过本地 mock 服务发送的端到端链路。
- 当前 `Medium_Phone_2(AVD) - 16` 未解析到 `IMAGE_CAPTURE` 处理 Activity，拍照结果自动化需要换用带相机 App 的 AVD 或真机。
- 在配置真实 API Key 或本地 OpenAI-compatible 服务后，增加一次真实服务 smoke test，确认不同服务商对多模态请求体的兼容性。
- 继续扩充 App-Leap 应用别名与 deeplink 动作；第三方 App 内点击/填表仍需 AccessibilityService 方案单独设计。
- 真机验证相机、麦克风、短信、通知、文件选择器和不同 Android 版本的权限行为。
- 根据真实 vision 模型测试结果，继续调优图片压缩尺寸和质量参数。
- Android Studio 若使用“卸载后安装”、`pm clear`、清除应用数据或更换 applicationId，系统仍会删除本地 Room/DataStore 数据；需要在本机运行配置中关闭会清数据的部署选项，并用真实升级安装流程复测数据保留。
- 对 DeepSeek/Qwen/MiMo 等真实服务商分别做思考模式 smoke test，确认各家的 OpenAI-compatible 字段兼容性；如某服务商字段不同，需要按 provider/model 增加适配。

## 测试记录

- 2026-06-09 `assembleDebug`：通过，生成 debug APK。发现项：Kapt 提示暂不支持 Kotlin 2.0+ 并回退到 1.9；后续已在第三十三阶段迁移 Room KSP 处理。
- 2026-06-09 `testDebugUnitTest`：通过。发现项：当前测试仍偏模板级，未覆盖新增 Claw 指令和多模态采集流程，已加入后续待做。
- 2026-06-09 敏感信息/占位扫描：通过，未发现待办占位、待修标记或疑似硬编码 API Key。
- 2026-06-09 `git diff --check`：通过，无空白错误。发现项：多文件提示 LF 将在 Git 触碰时转换为 CRLF，已加入后续待做。
- 2026-06-09 阶段 7-8：抽离 Claw 指令解析为 `ClawCommandParser`，新增业务单元测试覆盖拍照、文档、录音、下载、短信和系统跳转指令。发现项：首次测试暴露“下载文件”被文档关键词抢先识别、短信正文中的“短信”被全局移除，均已修复并由测试锁定。
- 2026-06-09 阶段 9 `testDebugUnitTest`：通过，Claw 指令解析业务测试已纳入本机单测。
- 2026-06-09 阶段 9 `assembleDebug`：通过，抽离解析模块后 App 仍可正常构建。
- 2026-06-09 第十阶段：确认本机 Android SDK adb 位于 `F:\Android SDK\platform-tools\adb.exe`，模拟器 `emulator-5554` 在线。
- 2026-06-09 第十一至十二阶段：新增 Compose UI 测试标签和 `ChatClawInstrumentedTest`，覆盖首次进入 App、开启 Claw、本地指令写入会话结果。
- 2026-06-09 第十三阶段 `testDebugUnitTest`：通过。
- 2026-06-09 第十三阶段 `assembleDebug`：通过。
- 2026-06-09 第十三阶段 `connectedDebugAndroidTest`：通过，模拟器 `Medium_Phone_2(AVD) - 16` 上 2 个测试、0 失败。发现项：当前模拟器测试只覆盖无权限弹窗的本地 Claw 指令，拍照、文件选择、录音权限、短信权限和短信二次确认仍需后续 UI 自动化或真机验证。
- 2026-06-09 第十三阶段 敏感信息/占位扫描：通过，未发现待办占位、待修标记或疑似硬编码 API Key。
- 2026-06-09 第十三阶段 `git diff --check`：通过，无空白错误；仍有既有 LF/CRLF 转换提示。
- 2026-06-09 第十四至十五阶段：新增短信二次确认模拟器测试，用 instrumentation shell grant 预授予 `SEND_SMS`，验证短信指令不会直接发送，必须先显示 App 内确认框。
- 2026-06-09 第十六阶段 首次 `connectedDebugAndroidTest`：未通过，原因是当前 Compose UI Test 依赖中 `assertExists` 导入不可用。处理：改为 `waitUntil + onAllNodesWithText` 检测确认框。
- 2026-06-09 第十六阶段 `testDebugUnitTest`：通过。
- 2026-06-09 第十六阶段 `assembleDebug`：通过。
- 2026-06-09 第十六阶段 第二次 `connectedDebugAndroidTest`：通过，模拟器 `Medium_Phone_2(AVD) - 16` 上 3 个测试、0 失败，新增 `clawSmsRequiresInAppConfirmationBeforeSending`。
- 2026-06-09 第十六阶段 敏感信息/占位扫描：通过，未发现待办占位、待修标记或疑似硬编码 API Key。
- 2026-06-09 第十六阶段 `git diff --check`：通过，无空白错误；仍有既有 LF/CRLF 转换提示。
- 2026-06-09 第十七至十九阶段：新增 UIAutomator 依赖、附件菜单测试标签和录音面板测试标签；新增短信权限拒绝、附件菜单多模态动作、录音面板打开三个模拟器测试。
- 2026-06-09 第二十阶段 `testDebugUnitTest`：通过。
- 2026-06-09 第二十阶段 `assembleDebug`：通过。发现项：构建提示部分 native 库无法 strip，已按原样打包，不影响 debug 构建。
- 2026-06-09 第二十阶段 `connectedDebugAndroidTest`：通过，模拟器 `Medium_Phone_2(AVD) - 16` 上 6 个测试、0 失败。新增覆盖：`clawSmsPermissionDeniedWritesLocalResult`、`attachmentMenuShowsMultimodalActions`、`recordingMenuOpensRecordingPanel`。
- 2026-06-09 第二十阶段 敏感信息/占位扫描：通过，未发现待办占位、待修标记或疑似硬编码 API Key。
- 2026-06-09 第二十阶段 `git diff --check`：通过，无空白错误；仍有既有 LF/CRLF 转换提示。
- 2026-06-09 第二十一至二十二阶段：新增录音开始/结束、待发送附件测试标签；新增 `recordingCanCreatePendingAudioAttachment` 和 `documentPickerCancelReturnsToChatWithMessage` 模拟器测试。
- 2026-06-09 第二十三阶段 `testDebugUnitTest`：通过。
- 2026-06-09 第二十三阶段 `assembleDebug`：通过。
- 2026-06-09 第二十三阶段 `connectedDebugAndroidTest`：通过，模拟器 `Medium_Phone_2(AVD) - 16` 上 8 个测试、0 失败。新增覆盖：录音从开始到结束并生成待发送录音附件，文档选择器打开后返回取消并提示“未选择文档”。
- 2026-06-09 第二十三阶段 敏感信息/占位扫描：通过，未发现待办占位、待修标记或疑似硬编码 API Key。
- 2026-06-09 第二十三阶段 `git diff --check`：通过，无空白错误；仍有既有 LF/CRLF 转换提示。
- 2026-06-09 第二十四阶段：新增 `documentPickerCanCreatePendingDocumentAttachment`，测试会在 `/sdcard/Download` 预置 `aiot_picker_probe.txt`，通过 DocumentsUI 实际选中文档，验证 App 生成“待发送文档”附件。
- 2026-06-09 第二十五阶段：通过 adb `cmd package resolve-activity android.media.action.IMAGE_CAPTURE` 探测当前 AVD，结果为无处理 Activity。发现项：拍照结果自动化需换用带相机 App 的 AVD 或真机。
- 2026-06-09 第二十六阶段 `testDebugUnitTest`：通过。
- 2026-06-09 第二十六阶段 `assembleDebug`：通过。
- 2026-06-09 第二十六阶段 `connectedDebugAndroidTest`：通过，模拟器 `Medium_Phone_2(AVD) - 16` 上 9 个测试、0 失败。新增覆盖：系统文件选择器实际返回文本文档并生成待发送文档附件。
- 2026-06-09 第二十六阶段 敏感信息/占位扫描：通过，未发现待办占位、待修标记或疑似硬编码 API Key。
- 2026-06-09 第二十六阶段 `git diff --check`：通过，无空白错误；仍有既有 LF/CRLF 转换提示。
- 2026-06-09 第二十七至二十九阶段：新增 `ChatRepositoryAttachmentInstrumentedTest`，用 in-memory Room 与 fake `LlmApiService` 验证附件发送后进入模型上下文的仓储链路。新增覆盖：文档 `textPreview` 会附加到用户上下文、录音会提示当前通道不能直接转写、未开启 vision 时图片降级为文本提示、开启 vision 时本地图片会转为 `data:image/jpeg;base64,...`。
- 2026-06-09 第二十九阶段 `connectedDebugAndroidTest`：通过，模拟器 `Medium_Phone_2(AVD) - 16` 上 13 个测试、0 失败，其中新增 4 个仓储附件链路测试。发现项：当前为 fake LLM 验证，不覆盖真实 HTTP 请求体序列化；仓储测试复用目标 App DataStore，虽使用唯一 profile 激活但仍建议后续增加测试专用隔离。
- 2026-06-09 第二十九阶段 `testDebugUnitTest`：通过，Claw 指令解析单测仍为 10 个测试、0 失败。
- 2026-06-09 第二十九阶段 `assembleDebug`：通过，debug APK 构建未受新增仪器测试影响。发现项：Gradle 空代理端口提示会回落默认端口；Kapt/Kotlin 兼容提示后续已在第三十三阶段处理。
- 2026-06-09 第三十阶段：梳理剩余无需真机的待做项，确认可继续完成：OpenAI-compatible 请求体序列化验证、仓储测试 DataStore 隔离、Room KSP 迁移。真实拍照、真实短信发送、真实麦克风设备差异和真实服务商 smoke test 仍需对应设备或密钥条件。
- 2026-06-09 第三十一阶段：新增 `OpenAiCompatibleClientTest`，使用 MockWebServer 覆盖普通文本消息、多图片 vision 消息、`/v1/chat/completions` URL 拼接、Authorization 头、usage 解析和 API 错误消息。首次 `testDebugUnitTest` 未通过，原因是 JVM 单测中的 Android mock `org.json` 方法不可用；处理：新增 `org.json:json` 测试依赖后通过。
- 2026-06-09 第三十二阶段：`SettingsStore` 支持注入测试专用 DataStore，`ChatRepositoryAttachmentInstrumentedTest` 改用独立临时 `preferences_pb` 文件。首次 `connectedDebugAndroidTest` 编译未通过，原因是 androidTest 直接使用 `PreferenceDataStoreFactory` 时触发 Kapt androidTest stub 生成错误；处理：将测试工厂上移为 `SettingsStore.createForTest(...)`，androidTest 只传入临时文件后通过。
- 2026-06-09 第三十三阶段：Room 编译器从 Kapt 迁移到 KSP，移除 app 模块 Kapt 插件，改用 `ksp(libs.androidx.room.compiler)`。`testDebugUnitTest` 通过，Kapt/Kotlin 2.0 回退提示消失。
- 2026-06-09 第三十三阶段 `assembleDebug`：通过，debug APK 构建正常；仍有部分 native 库无法 strip 的既有提示，不影响 debug 构建。
- 2026-06-09 第三十三阶段 `connectedDebugAndroidTest`：通过，模拟器 `Medium_Phone_2(AVD) - 16` 上 13 个测试、0 失败；KSP 下 androidTest 和 Room in-memory 数据库链路正常。
- 2026-06-09 第三十四阶段 `testDebugUnitTest`：通过，KSP 插件与版本目录清理后本机单测仍正常；当前 JVM 单测包含 4 个 OpenAI-compatible 请求体测试。
- 2026-06-09 第三十四阶段 敏感信息/占位扫描：通过，未发现待办占位、待修标记或疑似硬编码 API Key。
- 2026-06-09 第三十四阶段 `git diff --check`：通过，无空白错误；仍有既有 LF/CRLF 转换提示，已保留在后续待做。
- 2026-06-10 第三十五阶段：根据真机反馈修复 Claw `打开qq` 被回退到浏览器搜索的问题。原因：应用启动分支使用 `findLaunchIntent(...) ?: ACTION_WEB_SEARCH`，且 Android 11+ 缺少常见应用包可见性声明时会更容易返回 null。处理：新增 `AppLeapCommandResolver` 解析应用别名和包名，manifest 增加 QQ、微信、支付宝、Chrome、地图等 `<queries>`；明确打开应用失败时写入失败消息，不再回退搜索。
- 2026-06-10 第三十五阶段 `testDebugUnitTest`：通过，新增覆盖 `打开qq` 解析、QQ 候选包、直接包名 `com.example.missing.app` 不被错误裁剪、浏览器搜索词清理。
- 2026-06-10 第三十五阶段 `assembleDebug`：通过。
- 2026-06-10 第三十五阶段 单条真机仪器测试：通过，在 `REA-AN00 - 15` 上运行 `clawOpenMissingAppWritesFailureInsteadOfBrowserFallback`，验证明确打开不存在应用时会写入“未找到可打开的应用”，不会回退浏览器搜索。发现项：完整 `connectedDebugAndroidTest` 在该真机上仍有既有 DocumentsUI 文件选择差异，`documentPickerCanCreatePendingDocumentAttachment` 找不到预置文件；后续需针对真机文件选择器适配。
- 2026-06-11 第三十六阶段：按反馈优化 Claw 与模型配置体验，移除底部 Claw 导航入口，压缩聊天顶部/输入区和主页面顶部留白，重做设置页信息层级，支持同供应商多模型配置、简洁模型切换、思考模式配置和聊天页快速开关；持久化规则补充 DataStore/Room 备份范围。
- 2026-06-11 第三十六阶段 `testDebugUnitTest`：通过，新增 `sendsReasoningParametersWhenThinkingIsEnabled` 覆盖思考模式请求体参数。
- 2026-06-11 第三十六阶段 `assembleDebug`：通过，debug APK 构建正常。
- 2026-06-11 第三十六阶段 单条真机仪器测试：通过，在 `REA-AN00 - 15` 上运行 `clawOpenMissingAppWritesFailureInsteadOfBrowserFallback`，验证安装到设备后的基础 Claw 打开应用失败回写路径仍正常。
- 2026-06-11 第三十六阶段 敏感信息/占位扫描：通过，源码未发现 TODO/FIXME 或疑似硬编码 API Key；命中项均为文档中记录的历史测试命令。
- 2026-06-11 第三十六阶段 `git diff --check`：通过，无空白错误；仍有既有 LF/CRLF 转换提示，保留在后续待做。
- 2026-06-11 第三十七阶段：完成当前环境可做的后续提升：语音转写配置、OpenAI-compatible multipart 转写客户端、录音转写上下文注入与缓存、App-Leap 拨号/地图/应用市场动作、androidTest MockWebServer 依赖、UI 录音端到端 mock 测试、仓库 `.gitattributes` 行尾策略。
- 2026-06-11 第三十七阶段 `testDebugUnitTest`：通过，新增覆盖音频转写 multipart 请求和 App-Leap 拨号/地图/应用市场解析。
- 2026-06-11 第三十七阶段 `assembleDebug`：通过，debug APK 构建正常。
- 2026-06-11 第三十七阶段 单条真机仪器测试：首次运行 `recordingAttachmentSendsThroughMockTranscriptionAndChatService` 未通过，原因是 Compose 中“新建”文本匹配到两个节点；处理为 `onAllNodesWithText(...).onFirst()` 后重跑通过。
- 2026-06-11 第三十七阶段 仓储级真机仪器测试：通过，运行 `audioAttachmentWithTranscriptionAddsTranscriptToContext` 和 `audioTranscriptionIsCachedForRegeneration`，验证录音转写进入上下文且重新生成时复用缓存。
- 2026-06-11 第三十七阶段 Claw 真机回归：通过，运行 `clawOpenMissingAppWritesFailureInsteadOfBrowserFallback`，验证 App-Leap 动作扩展后，明确打开不存在应用仍不会回退到浏览器搜索。
- 2026-06-11 第三十七阶段 敏感信息/占位扫描：通过，源码未发现 TODO/FIXME 或疑似硬编码 API Key；命中项均为文档中记录的历史测试命令。
- 2026-06-11 第三十七阶段 `git diff --check`：通过，无空白错误；新增 `.gitattributes` 后工作区提示后续将按 LF 归一化。
