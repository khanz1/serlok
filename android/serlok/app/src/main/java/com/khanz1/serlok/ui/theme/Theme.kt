package com.khanz1.serlok.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SerlokLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F0EC),
    onPrimaryContainer = Color(0xFF064A45),
    secondary = Color(0xFF356A66),
    onSecondary = Color.White,
    tertiary = Color(0xFF466460),
    onTertiary = Color.White,
    error = LightEmergency,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF6B1014),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.58f),
    inverseSurface = Color(0xFF243438),
    inverseOnSurface = Color(0xFFECF3F1),
    inversePrimary = SerlokPrimary,
)

object SerlokSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
}

@Composable
fun SerlokTheme(content: @Composable () -> Unit) {
    SideEffect {
        if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    val colorScheme = SerlokLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SerlokTypography,
        shapes = AppShapes,
    ) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorScheme.background,
                contentColor = colorScheme.onBackground,
                content = content,
            )
        }
    }
}
