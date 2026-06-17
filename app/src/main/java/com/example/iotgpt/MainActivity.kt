package com.example.iotgpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.iotgpt.core.notification.NotificationHelper
import com.example.iotgpt.core.preferences.SettingsStore
import com.example.iotgpt.core.preferences.ThemeMode
import com.example.iotgpt.navigation.AppNavigation
import com.example.iotgpt.ui.theme.LotTheme

/**
 * App host Activity. It owns the Compose entry point and delegates app screens
 * to the navigation layer.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper(this).ensureChannels()
        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { SettingsStore(applicationContext) }
            val themeMode by settingsStore.themeMode.collectAsState(initial = ThemeMode.System)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            LotTheme(darkTheme = darkTheme, dynamicColor = false) {
                AppNavigation()
            }
        }
    }
}
