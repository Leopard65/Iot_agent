package com.example.iotgpt.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object LotSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object LotRadius {
    val sm = 6.dp
    val md = 8.dp
    val lg = 12.dp
}

object LotMotion {
    const val fast = 120
    const val normal = 220
    const val slow = 320
}

object LotColors {
    val Claw = Color(0xFF22C55E)
    val OnClaw = Color(0xFF06120B)
    val Warning = Color(0xFFF59E0B)
    val ChartPalette = listOf(
        Color(0xFF4F46E5), // M1 靛
        Color(0xFFF59E0B), // M2 琥珀
        Color(0xFF14B8A6), // M3 蓝绿
        Color(0xFFF43F5E), // M4 玫红
        Color(0xFF0EA5E9), // M5 天蓝
        Color(0xFFA855F7)  // M6 紫
    )
}
