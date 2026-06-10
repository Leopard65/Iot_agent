package com.example.iotgpt.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.iotgpt.feature.chat.ui.ChatScreen
import com.example.iotgpt.feature.settings.ui.SettingsScreen
import com.example.iotgpt.feature.stats.ui.StatsScreen
import com.example.iotgpt.feature.welcome.ui.LaunchWelcomeScreen
import com.example.iotgpt.feature.welcome.ui.WelcomeScreen
import com.example.iotgpt.core.preferences.SettingsStore
import kotlinx.coroutines.launch

/**
 * Root navigation graph for the AIoT Assistant app shell.
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context.applicationContext) }
    val onboardingCompleted by settingsStore.onboardingCompleted.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var showLaunchWelcome by rememberSaveable { mutableStateOf(true) }

    if (showLaunchWelcome) {
        LaunchWelcomeScreen(
            onFinished = {
                showLaunchWelcome = false
            }
        )
        return
    }

    if (!onboardingCompleted) {
        WelcomeScreen(
            onFinished = {
                scope.launch {
                    settingsStore.saveOnboardingCompleted(true)
                }
            }
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MainRoute.Chat.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                bottomNavigationRoutes.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            BusinessNavIcon(route = destination, selected = selected)
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainRoute.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MainRoute.Chat.route) {
                ChatScreen()
            }
            composable(MainRoute.Stats.route) {
                StatsScreen()
            }
            composable(MainRoute.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
private fun BusinessNavIcon(
    route: MainRoute,
    selected: Boolean
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val accent = if (selected) active else inactive
    val glow = if (selected) active.copy(alpha = 0.16f) else Color.Transparent

    Box(
        modifier = Modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            drawCircle(color = glow, radius = size.minDimension * 0.48f)
            when (route) {
                MainRoute.Chat -> drawConsultingIcon(accent)
                MainRoute.Stats -> drawAnalyticsIcon(accent, selected)
                MainRoute.Settings -> drawControlDialIcon(accent, selected)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConsultingIcon(color: Color) {
    val center = Offset(size.width * 0.45f, size.height * 0.5f)
    val nodes = listOf(
        Offset(size.width * 0.2f, size.height * 0.25f),
        Offset(size.width * 0.2f, size.height * 0.74f),
        Offset(size.width * 0.68f, size.height * 0.22f),
        Offset(size.width * 0.74f, size.height * 0.66f)
    )
    nodes.forEach { node ->
        val path = Path().apply {
            moveTo(center.x, center.y)
            cubicTo(center.x, node.y, node.x, center.y, node.x, node.y)
        }
        drawPath(path, color.copy(alpha = 0.58f), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 2.1.dp.toPx(), center = node)
    }
    drawCircle(color.copy(alpha = 0.22f), radius = 8.6.dp.toPx(), center = center)
    drawCircle(color, radius = 4.2.dp.toPx(), center = center)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.56f, size.height * 0.36f),
        size = Size(size.width * 0.25f, size.height * 0.17f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 1.7.dp.toPx())
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAgentChipIcon(
    color: Color,
    selected: Boolean
) {
    val chipTopLeft = Offset(size.width * 0.26f, size.height * 0.24f)
    val chipSize = Size(size.width * 0.48f, size.height * 0.48f)
    val pinAlpha = if (selected) 0.96f else 0.54f
    drawRoundRect(
        color = color.copy(alpha = 0.15f),
        topLeft = chipTopLeft,
        size = chipSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx())
    )
    drawRoundRect(
        color = color,
        topLeft = chipTopLeft,
        size = chipSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
        style = Stroke(width = 1.8.dp.toPx())
    )
    repeat(4) { index ->
        val t = 0.32f + index * 0.12f
        drawLine(color.copy(alpha = pinAlpha), Offset(size.width * 0.14f, size.height * t), Offset(size.width * 0.25f, size.height * t), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(color.copy(alpha = pinAlpha), Offset(size.width * 0.75f, size.height * t), Offset(size.width * 0.86f, size.height * t), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(color.copy(alpha = pinAlpha), Offset(size.width * t, size.height * 0.14f), Offset(size.width * t, size.height * 0.25f), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(color.copy(alpha = pinAlpha), Offset(size.width * t, size.height * 0.75f), Offset(size.width * t, size.height * 0.86f), 1.6.dp.toPx(), StrokeCap.Round)
    }
    drawCircle(color, radius = 2.6.dp.toPx(), center = Offset(size.width * 0.5f, size.height * 0.5f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnalyticsIcon(
    color: Color,
    selected: Boolean
) {
    val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension * 0.39f, center = center)
    rotate(if (selected) 18f else 0f, pivot = center) {
        drawArc(color.copy(alpha = 0.72f), 20f, 260f, false, style = stroke)
        drawArc(color.copy(alpha = 0.38f), 300f, 44f, false, style = stroke)
    }
    val widths = size.width * 0.13f
    val base = size.height * 0.72f
    listOf(0.48f, 0.34f, 0.58f).forEachIndexed { index, heightRatio ->
        val x = size.width * (0.27f + index * 0.18f)
        val h = size.height * heightRatio
        drawRoundRect(
            color = color.copy(alpha = 0.9f - index * 0.12f),
            topLeft = Offset(x, base - h),
            size = Size(widths, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawControlDialIcon(
    color: Color,
    selected: Boolean
) {
    val radius = size.minDimension * 0.37f
    drawCircle(color.copy(alpha = 0.14f), radius = radius, center = center)
    drawCircle(color, radius = radius, center = center, style = Stroke(width = 1.6.dp.toPx()))
    repeat(8) { index ->
        rotate(index * 45f, pivot = center) {
            drawLine(
                color.copy(alpha = if (selected) 0.9f else 0.54f),
                Offset(center.x, center.y - radius - 1.dp.toPx()),
                Offset(center.x, center.y - radius + 3.dp.toPx()),
                1.3.dp.toPx(),
                StrokeCap.Round
            )
        }
    }
    val path = Path().apply {
        moveTo(size.width * 0.25f, size.height * 0.58f)
        lineTo(size.width * 0.42f, size.height * 0.45f)
        lineTo(size.width * 0.54f, size.height * 0.56f)
        lineTo(size.width * 0.75f, size.height * 0.34f)
    }
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(size.width * 0.75f, size.height * 0.34f))
}
