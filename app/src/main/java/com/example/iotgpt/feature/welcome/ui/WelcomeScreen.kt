package com.example.iotgpt.feature.welcome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.iotgpt.core.components.AppPage
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone
import com.example.iotgpt.core.testing.AppTestTags

/**
 * First-launch onboarding for the lot app.
 */
@Composable
fun WelcomeScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = onboardingPages[pageIndex]
    val isLastPage = pageIndex == onboardingPages.lastIndex

    AppPage(
        title = "欢迎使用 lot",
        subtitle = "面向物联网专业的 AI 助理",
        modifier = modifier,
        trailing = {
            StatusPill("${pageIndex + 1}/${onboardingPages.size}", tone = StatusTone.Primary)
        }
    ) {
        LinearProgressIndicator(
            progress = { (pageIndex + 1).toFloat() / onboardingPages.size.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )

        AppSectionCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp)
        ) {
            StatusPill(page.tag, tone = StatusTone.Success)
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        AppSectionCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(14.dp)
        ) {
            Text(
                text = "本页准备内容",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = page.readyText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            page.readyItems.forEach { item ->
                ReadyItemRow(item)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                enabled = pageIndex > 0,
                onClick = { pageIndex-- }
            ) {
                Text("上一步")
            }
            Button(
                modifier = Modifier.testTag(AppTestTags.WELCOME_NEXT),
                onClick = {
                    if (isLastPage) {
                        onFinished()
                    } else {
                        pageIndex++
                    }
                }
            ) {
                Text(if (isLastPage) "开始使用" else "下一步")
            }
        }
    }
}

@Composable
private fun ReadyItemRow(item: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusPill("就绪", tone = StatusTone.Success)
    }
}

private data class OnboardingPage(
    val title: String,
    val description: String,
    val tag: String,
    val readyText: String,
    val readyItems: List<String>
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "AI 物联网问答",
        description = "围绕传感器、嵌入式开发、MQTT、边缘计算和设备联网问题进行专业问答。",
        tag = "Chat Completions",
        readyText = "对话页已经准备好。真正联网回答需要先在设置页填写对应模型的 API Key。",
        readyItems = listOf(
            "本地会话历史",
            "模型快速切换",
            "异步回复与中断"
        )
    ),
    OnboardingPage(
        title = "多模态采集",
        description = "在会话中添加图片、文档和录音；文本、Markdown、PDF 和 DOCX 会尽量提取正文后发送给模型。",
        tag = "Camera · File · Audio",
        readyText = "采集入口已启用。图片解析取决于当前模型是否开启视觉能力，录音会先作为附件保存。",
        readyItems = listOf(
            "拍照与图库图片",
            "文档正文提取",
            "可定时录音"
        )
    ),
    OnboardingPage(
        title = "数据统计与模型配置",
        description = "记录会话、模型调用、Token/字符估算和网络状态，支持多个兼容模型服务配置。",
        tag = "Room · DataStore",
        readyText = "本地数据库和设置项已可用。首次使用建议先进入设置页测试模型连接。",
        readyItems = listOf(
            "本地统计面板",
            "多模型配置",
            "连接测试入口"
        )
    )
)
