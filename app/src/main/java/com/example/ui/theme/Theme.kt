package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorDivider = Color(0xFFECEAF5)

private val AdZeroLightColorScheme = lightColorScheme(
    primary = AdZeroPurple,
    onPrimary = AdZeroWhite,
    primaryContainer = AdZeroLavenderSurface,
    onPrimaryContainer = AdZeroPurpleDark,
    secondary = AdZeroPurpleSoft,
    onSecondary = AdZeroWhite,
    secondaryContainer = AdZeroLavenderBg,
    onSecondaryContainer = AdZeroNavyText,
    tertiary = AdZeroSuccess,
    onTertiary = AdZeroWhite,
    tertiaryContainer = AdZeroSuccessContainer,
    onTertiaryContainer = AdZeroSuccess,
    background = AdZeroWhite,
    onBackground = AdZeroNavyText,
    surface = AdZeroWhite,
    onSurface = AdZeroNavyText,
    surfaceVariant = AdZeroLavenderBg,
    onSurfaceVariant = AdZeroMutedText,
    surfaceContainerLowest = AdZeroWhite,
    surfaceContainerLow = AdZeroLavenderBg,
    surfaceContainer = AdZeroLavenderBg,
    surfaceContainerHigh = AdZeroLavenderSurface,
    surfaceContainerHighest = AdZeroLavenderBorder,
    outline = AdZeroLavenderBorder,
    outlineVariant = ColorDivider,
    error = AdZeroError,
    onError = AdZeroWhite,
    errorContainer = AdZeroErrorContainer,
    onErrorContainer = AdZeroError
)

val AdZeroShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AdZeroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AdZeroLightColorScheme,
        typography = AdZeroTypography,
        shapes = AdZeroShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    AdZeroTheme(content = content)
}
