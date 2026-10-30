package com.khanz1.serlok.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/** Wordless Serlok mark: a location pin carrying a clear safety exclamation. */
@Composable
fun SerlokMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = modifier.then(
            if (contentDescription != null) Modifier.semantics {
                this.contentDescription = contentDescription
            } else Modifier,
        ),
    ) {
        val stroke = size.minDimension * 0.085f
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.94f)
            cubicTo(
                size.width * 0.39f,
                size.height * 0.76f,
                size.width * 0.19f,
                size.height * 0.59f,
                size.width * 0.19f,
                size.height * 0.38f,
            )
            cubicTo(
                size.width * 0.19f,
                size.height * 0.16f,
                size.width * 0.33f,
                size.height * 0.06f,
                size.width * 0.5f,
                size.height * 0.06f,
            )
            cubicTo(
                size.width * 0.67f,
                size.height * 0.06f,
                size.width * 0.81f,
                size.height * 0.16f,
                size.width * 0.81f,
                size.height * 0.38f,
            )
            cubicTo(
                size.width * 0.81f,
                size.height * 0.59f,
                size.width * 0.61f,
                size.height * 0.76f,
                size.width * 0.5f,
                size.height * 0.94f,
            )
            close()
        }
        drawPath(path, color = color, style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.25f),
            end = Offset(size.width * 0.5f, size.height * 0.48f),
            strokeWidth = stroke,
        )
        drawCircle(color, radius = stroke * 0.58f, center = Offset(size.width * 0.5f, size.height * 0.62f))
    }
}

@Composable
fun DeterministicAvatar(
    seed: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = listOf(
        Color(0xFFD2F0EC),
        Color(0xFFFFE0CC),
        Color(0xFFE8DEF8),
        Color(0xFFDCE7FF),
        Color(0xFFFFDAD8),
    )
    val index = seed.hashCode().absoluteValue % colors.size
    val initials = name.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(2)
        .joinToString("") { it.take(1).uppercase() }.ifBlank { "S" }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(colors[index]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color(0xFF163B3A),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
