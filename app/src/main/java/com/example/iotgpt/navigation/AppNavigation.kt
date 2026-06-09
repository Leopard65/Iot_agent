package com.example.iotgpt.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.iotgpt.feature.agent.ui.AgentScreen
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
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = destination.iconText,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
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
            composable(MainRoute.Agent.route) {
                AgentScreen()
            }
            composable(MainRoute.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
