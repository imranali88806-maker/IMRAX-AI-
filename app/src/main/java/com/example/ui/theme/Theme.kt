package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ImraxDarkColorScheme = darkColorScheme(
    primary = CyanElectric,
    onPrimary = NavyAbyss,
    primaryContainer = NavySurfaceElevated,
    onPrimaryContainer = CyanSoft,
    secondary = BlueVibrant,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF142752),
    onSecondaryContainer = Color(0xFFBFD7FF),
    tertiary = PurpleGlow,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF28194E),
    onTertiaryContainer = PurpleSoft,
    background = NavyAbyss,
    onBackground = TextPrimaryDark,
    surface = NavyDeep,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavySurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = NavyAbyss,
    surfaceContainerLow = NavyDeep,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavySurfaceElevated,
    surfaceContainerHighest = Color(0xFF1E2D58),
    outline = NavyBorder,
    outlineVariant = Color(0xFF1B2748),
    error = ErrorRedDark,
    onError = NavyAbyss
)

private val ImraxLightColorScheme = lightColorScheme(
    primary = BluePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF0A2563),
    secondary = CyanAccentLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = PurpleAccentLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF3B0764),
    background = LightCanvas,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFF),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE2E9F7),
    surfaceContainerHighest = Color(0xFFD5E0F3),
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = ErrorRedLight,
    onError = Color.White
)

private val ImraxShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ImraxAiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ImraxDarkColorScheme else ImraxLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ImraxTypography,
        shapes = ImraxShapes,
        content = content
    )
}
