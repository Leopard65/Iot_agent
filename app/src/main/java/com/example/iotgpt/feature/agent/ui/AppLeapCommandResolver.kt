package com.example.iotgpt.feature.agent.ui

internal object AppLeapCommandResolver {
    private val packageNameRegex = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$""")
    private val phoneRegex = Regex("""\+?\d[\d\s-]{2,}\d""")

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

    fun extractPhoneNumber(command: String): String? {
        return phoneRegex.find(command)
            ?.value
            ?.filter { it.isDigit() || it == '+' }
            ?.takeIf { it.length >= 3 }
    }

    fun extractMapQuery(command: String): String? {
        val trimmed = command.trim()
        val query = mapPrefixes.firstOrNull { prefix ->
            trimmed.startsWith(prefix, ignoreCase = true)
        }?.let { prefix ->
            trimmed.drop(prefix.length).trim()
        } ?: trimmed
            .replace("地图导航", "")
            .replace("导航", "")
            .replace("地图", "")
            .replace("navigate to", "", ignoreCase = true)
            .replace("navigate", "", ignoreCase = true)
            .replace("map", "", ignoreCase = true)
            .trim()
        return query.takeIf { it.isNotBlank() }
    }

    fun extractMarketTarget(command: String): String? {
        val trimmed = command.trim()
        val target = marketPrefixes.firstOrNull { prefix ->
            trimmed.startsWith(prefix, ignoreCase = true)
        }?.let { prefix ->
            trimmed.drop(prefix.length).trim()
        } ?: trimmed
            .replace("应用市场", "")
            .replace("应用商店", "")
            .replace("商店", "")
            .replace("市场", "")
            .replace("安装", "")
            .replace("下载", "")
            .replace("market", "", ignoreCase = true)
            .replace("store", "", ignoreCase = true)
            .trim()
        return target.takeIf { it.isNotBlank() }
    }

    fun isPackageName(value: String): Boolean {
        return packageNameRegex.matches(value.trim())
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
    private val mapPrefixes = listOf(
        "导航到",
        "地图导航到",
        "打开地图导航到",
        "去",
        "navigate to",
        "navigate",
        "map"
    )
    private val marketPrefixes = listOf(
        "打开应用市场",
        "打开应用商店",
        "应用市场搜索",
        "应用商店搜索",
        "安装",
        "下载应用",
        "market",
        "store"
    )
    private val leadingAppTokens = listOf(
        "应用",
        "app"
    )
}
