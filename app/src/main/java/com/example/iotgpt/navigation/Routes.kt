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
    Stats("stats", "统计", "图"),
    Agent("agent", "Claw", "爪"),
    Settings("settings", "设置", "设")
}

val bottomNavigationRoutes = listOf(
    MainRoute.Chat,
    MainRoute.Stats,
    MainRoute.Settings
)
