package com.example.iotgpt.feature.welcome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.iotgpt.core.components.AppPage
import com.example.iotgpt.core.components.AppSectionCard
import com.example.iotgpt.core.components.StatusPill
import com.example.iotgpt.core.components.StatusTone

/**
 * First-launch onboarding for the AIoT Assistant app.
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
        title = "欢迎使用 AIoT Assistant",
        subtitle = "面向物联网课程学习与工程演示",
        modifier = modifier,
        trailing = {
            StatusPill("${pageIndex + 1}/${onboardingPages.size}", tone = StatusTone.Primary)
        }
    ) {
        LinearProgressIndicator(
            progress = { (pageIndex + 1).toFloat() / onboardingPages.size.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusPill(page.tag, tone = StatusTone.Success)
        }

        AppSectionCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "加载状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = page.loadingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

private data class OnboardingPage(
    val title: String,
    val description: String,
    val tag: String,
    val loadingText: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "AI 物联网问答",
        description = "围绕传感器、嵌入式开发、MQTT、边缘计算和设备联网问题进行课程问答。",
        tag = "Chat Completions",
        loadingText = "正在准备对话工作区"
    ),
    OnboardingPage(
        title = "多模态采集",
        description = "在会话中添加图片、文档和录音，txt 文档可作为上下文发送给模型。",
        tag = "Camera · File · Audio",
        loadingText = "正在加载采集入口"
    ),
    OnboardingPage(
        title = "数据统计与模型配置",
        description = "记录会话、模型调用、Token/字符估算和网络状态，支持多个兼容模型服务配置。",
        tag = "Room · DataStore",
        loadingText = "正在同步本地设置"
    )
)
