package com.khanz1.serlok.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An animated pulsing ring indicator — concentric circles that expand
 * and fade out, conveying "active monitoring" status.
 */
@Composable
fun PulseIndicator(
    modifier: Modifier = Modifier,
    color: Color,
    size: Dp = 120.dp,
    rings: Int = 3,
) {
    val transition = rememberInfiniteTransition(label = "pulse")

    val phases = (0 until rings).map { i ->
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 2000,
                    delayMillis = i * (2000 / rings),
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_$i"
        )
        progress
    }

    Canvas(modifier = modifier.size(size)) {
        val center = this.center
        val maxRadius = this.size.minDimension / 2f

        phases.forEach { progress ->
            val radius = maxRadius * 0.3f + maxRadius * 0.7f * progress
            val alpha = (1f - progress).coerceIn(0f, 0.6f)
            drawCircle(
                color       = color.copy(alpha = alpha),
                radius      = radius,
                center      = center,
                style       = Stroke(width = 2.dp.toPx()),
            )
        }

        // Solid center dot
        drawCircle(
            color  = color,
            radius = maxRadius * 0.12f,
            center = center,
        )
    }
}
