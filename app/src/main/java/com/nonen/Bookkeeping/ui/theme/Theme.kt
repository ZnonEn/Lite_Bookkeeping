package com.nonen.Bookkeeping.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = InkPrimary,
    onPrimary = Color.White,
    primaryContainer = InkPrimary.copy(alpha = 0.1f),
    onPrimaryContainer = InkPrimary,
    secondary = InkSecondary,
    onSecondary = Color.White,
    secondaryContainer = InkSecondary.copy(alpha = 0.1f),
    onSecondaryContainer = InkSecondary,
    tertiary = AppleGreen,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = AppleRed,
)

private val DarkColors = darkColorScheme(
    primary = InkPrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = InkPrimaryDark.copy(alpha = 0.15f),
    onPrimaryContainer = InkPrimaryDark,
    secondary = InkSecondaryDark,
    onSecondary = Color.Black,
    secondaryContainer = InkSecondaryDark.copy(alpha = 0.2f),
    onSecondaryContainer = InkSecondaryDark,
    tertiary = AppleGreen,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = AppleRed,
)

// inkqilin-ledger 形状体系：8/12/18/24/32
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BookkeepingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
