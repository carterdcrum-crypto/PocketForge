package com.pocketforge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

private val BaseTypography = Typography()

val PocketForgeTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = FontFamily.SansSerif),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = FontFamily.SansSerif),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = FontFamily.SansSerif),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = FontFamily.SansSerif),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = FontFamily.SansSerif),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = FontFamily.SansSerif),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = FontFamily.SansSerif),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = FontFamily.SansSerif)
)

@Composable
fun PocketForgeBrandTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = PocketForgeTypography, content = content)
}
