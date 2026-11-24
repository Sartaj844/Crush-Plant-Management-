package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = SlateNavy,
    onPrimary = Color.White,
    primaryContainer = SlateNavyContainer,
    onPrimaryContainer = OnSlateNavyContainer,
    secondary = QuarryAmber,
    onSecondary = Color.White,
    secondaryContainer = QuarryAmberContainer,
    onSecondaryContainer = OnQuarryAmberContainer,
    tertiary = EmeraldProfit,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldProfitBg,
    onTertiaryContainer = OnEmeraldProfitBg,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonAlertBg,
    onErrorContainer = OnCrimsonAlertBg,
    background = StoneBackground,
    onBackground = SlateTextPrimary,
    surface = CrispSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = StoneSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    outline = SlateOutline,
    outlineVariant = SlateOutline.copy(alpha = 0.5f)
)

private val DarkColorScheme = darkColorScheme(
    primary = QuarryAmberBright,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = SlateNavyLight,
    onPrimaryContainer = DarkTextPrimary,
    secondary = QuarryAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF451A03),
    onSecondaryContainer = QuarryAmberContainer,
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF002114),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = EmeraldProfitBg,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = CrimsonAlertBg,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.5f)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CrusherErpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
