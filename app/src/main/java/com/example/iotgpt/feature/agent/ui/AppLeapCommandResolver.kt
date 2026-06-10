package com.example.iotgpt.feature.agent.ui

internal object AppLeapCommandResolver {
    fun toSearchQuery(command: String): String {
        return command.trim()
            .replace("打开浏览器搜索", "")
            .replace("浏览器搜索", "")
            .replace("打开浏览器", "")
            .replace("搜索", "")
            .replace("search", "", ignoreCase = true)
            .replace("browser", "", ignoreCase = true)
            .trim()
            .ifBlank { command }
    }

    fun extractAppLaunchTarget(command: String): String? {
        val trimmed = command.trim()
        val withoutAction = launchPrefixes.firstOrNull { prefix ->
            trimmed.startsWith(prefix, ignoreCase = true)
        }?.let { prefix ->
            trimmed.drop(prefix.length)
        } ?: trimmed
        val target = leadingAppTokens.fold(withoutAction.trim()) { current, token ->
            if (current.startsWith(token, ignoreCase = true)) {
                current.drop(token.length).trim()
            } else {
                current
            }
        }
        return target.takeIf { it.isNotBlank() }
    }

    fun packageCandidatesFor(target: String): List<String> {
        val normalized = target.trim()
        if (normalized.isBlank()) return emptyList()
        val aliases = commonAppPackages[normalized.lowercase()].orEmpty()
        return (aliases + normalized).distinct()
    }

    private val commonAppPackages = mapOf(
        "微信" to listOf("com.tencent.mm"),
        "wechat" to listOf("com.tencent.mm"),
        "qq" to listOf("com.tencent.mobileqq", "com.tencent.tim"),
        "tim" to listOf("com.tencent.tim"),
        "支付宝" to listOf("com.eg.android.AlipayGphone"),
        "alipay" to listOf("com.eg.android.AlipayGphone"),
        "chrome" to listOf("com.android.chrome", "com.google.android.apps.chrome"),
        "高德地图" to listOf("com.autonavi.minimap"),
        "百度地图" to listOf("com.baidu.BaiduMap"),
        "淘宝" to listOf("com.taobao.taobao"),
        "qq音乐" to listOf("com.tencent.qqmusic"),
        "腾讯会议" to listOf("com.tencent.wemeet.app"),
        "钉钉" to listOf("com.alibaba.android.rimet"),
        "飞书" to listOf("com.ss.android.lark")
    )

    private val launchPrefixes = listOf(
        "打开",
        "启动",
        "open",
        "launch"
    )
    private val leadingAppTokens = listOf(
        "应用",
        "app"
    )
}
