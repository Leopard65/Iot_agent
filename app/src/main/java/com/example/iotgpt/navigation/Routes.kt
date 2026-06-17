package com.example.iotgpt.navigation

/**
 * Bottom-level destinations used by the first-stage app shell.
 */
enum class MainRoute(
    val route: String,
    val label: String,
    val iconText: String
) {
    Chat("chat", "对话", "聊"),
    Agent("agent", "智能体", "爪"),
    Stats("stats", "统计", "图"),
    Settings("settings", "设置", "设")
}

val bottomNavigationRoutes = listOf(
    MainRoute.Chat,
    MainRoute.Agent,
    MainRoute.Stats,
    MainRoute.Settings
)
