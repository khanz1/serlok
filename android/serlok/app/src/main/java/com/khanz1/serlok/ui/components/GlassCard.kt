package com.khanz1.serlok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khanz1.serlok.ui.theme.SerlokGlass
import com.khanz1.serlok.ui.theme.SerlokGlassBorder

/**
 * A glassmorphism-styled card with semi-transparent background
 * and subtle border glow for a premium dark-mode look.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(SerlokGlass)
            .border(width = 1.dp, color = SerlokGlassBorder, shape = shape)
            .padding(16.dp),
        content = content,
    )
}
