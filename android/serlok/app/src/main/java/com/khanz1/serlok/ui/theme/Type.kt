package com.khanz1.serlok.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.khanz1.serlok.R

val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
)

private fun serlokText(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val SerlokTypography = Typography(
    displayLarge = serlokText(48, 56, FontWeight.ExtraBold, -0.7f),
    displayMedium = serlokText(40, 48, FontWeight.ExtraBold, -0.5f),
    displaySmall = serlokText(34, 42, FontWeight.Bold, -0.4f),
    headlineLarge = serlokText(30, 38, FontWeight.Bold, -0.3f),
    headlineMedium = serlokText(26, 34, FontWeight.Bold, -0.2f),
    headlineSmall = serlokText(22, 30, FontWeight.Bold, -0.1f),
    titleLarge = serlokText(20, 28, FontWeight.Bold),
    titleMedium = serlokText(16, 24, FontWeight.SemiBold, 0.05f),
    titleSmall = serlokText(14, 20, FontWeight.SemiBold, 0.05f),
    bodyLarge = serlokText(16, 24, FontWeight.Normal, 0.1f),
    bodyMedium = serlokText(14, 21, FontWeight.Normal, 0.1f),
    bodySmall = serlokText(12, 18, FontWeight.Normal, 0.15f),
    labelLarge = serlokText(14, 20, FontWeight.Bold, 0.05f),
    labelMedium = serlokText(12, 16, FontWeight.SemiBold, 0.2f),
    labelSmall = serlokText(11, 16, FontWeight.SemiBold, 0.35f),
)
