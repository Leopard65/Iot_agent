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
    val Ai = Color(0xFF2563EB)
    val Iot = Color(0xFF10B981)
    val Claw = Color(0xFF22C55E)
    val OnClaw = Color(0xFF06120B)
    val Warning = Color(0xFFF59E0B)
    val ChartPalette = listOf(
        Color(0xFF2563EB),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFF0891B2),
        Color(0xFF7C3AED),
        Color(0xFFEF4444)
    )
}
