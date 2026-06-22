package com.example.iotgpt.feature.welcome.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Short launch welcome page shown before onboarding or the main app shell.
 */
@Composable
fun LaunchWelcomeScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "launch-welcome")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        delay(1850)
        onFinished()
    }

    val backgroundTop = MaterialTheme.colorScheme.surface
    val backgroundBottom = MaterialTheme.colorScheme.surfaceContainerHighest
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(backgroundTop, backgroundBottom)
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        TopologyCanvas(
            pulse = pulse,
            primary = primary,
            secondary = secondary,
            lineColor = onSurfaceVariant.copy(alpha = 0.22f)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            LogoMark(
                pulse = pulse,
                primary = primary,
                secondary = secondary
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "lot",
                    style = MaterialTheme.typography.headlineMedium,
                    color = onSurface,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "清晰思考，随时协助",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            LinearProgressIndicator(
                progress = { pulse },
                modifier = Modifier.fillMaxWidth(0.56f)
            )
        }
    }
}

@Composable
private fun LogoMark(
    pulse: Float,
    primary: Color,
    secondary: Color
) {
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.28f + pulse * 0.16f),
                        secondary.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            drawCircle(
                color = primary.copy(alpha = 0.42f),
                radius = radius * (0.74f + pulse * 0.08f),
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = secondary.copy(alpha = 0.56f),
                radius = radius * 0.44f,
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
        Text(
            text = "lot",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TopologyCanvas(
    pulse: Float,
    primary: Color,
    secondary: Color,
    lineColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val nodes = listOf(
            Offset(width * 0.12f, height * 0.18f),
            Offset(width * 0.34f, height * 0.24f),
            Offset(width * 0.68f, height * 0.16f),
            Offset(width * 0.88f, height * 0.32f),
            Offset(width * 0.18f, height * 0.68f),
            Offset(width * 0.42f, height * 0.78f),
            Offset(width * 0.74f, height * 0.72f),
            Offset(width * 0.90f, height * 0.84f)
        )
        val links = listOf(
            0 to 1,
            1 to 2,
            2 to 3,
            1 to 5,
            4 to 5,
            5 to 6,
            6 to 7,
            3 to 6,
            0 to 4
        )

        links.forEachIndexed { index, link ->
            val start = nodes[link.first]
            val end = nodes[link.second]
            drawLine(
                color = lineColor.copy(alpha = 0.14f + pulse * 0.14f),
                start = start,
                end = end,
                strokeWidth = if (index % 3 == 0) 1.6.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        nodes.forEachIndexed { index, node ->
            val color = if (index % 2 == 0) primary else secondary
            drawCircle(
                color = color.copy(alpha = 0.32f + pulse * 0.34f),
                radius = (2.2f + (index % 3) + pulse * 1.4f).dp.toPx(),
                center = node
            )
        }
    }
}
