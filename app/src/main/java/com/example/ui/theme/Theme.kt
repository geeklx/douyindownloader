package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemePreset(val displayName: String) {
    DOUYIN_DARK("抖音极客黑"),
    CLASSIC_LIGHT("致美极简白"),
    CYBERPUNK("霓虹赛博"),
    FOREST_ZEN("和煦温雅")
}

private val DouyinDarkColorScheme = darkColorScheme(
    primary = DouyinDarkPrimary,
    onPrimary = Color.White,
    primaryContainer = DouyinDarkSurface,
    onPrimaryContainer = Color.White,
    secondary = DouyinDarkSecondary,
    onSecondary = Color.Black,
    secondaryContainer = DouyinDarkSurface,
    onSecondaryContainer = Color.White,
    background = DouyinDarkBg,
    onBackground = DouyinDarkOnSurface,
    surface = DouyinDarkSurface,
    onSurface = DouyinDarkOnSurface,
    surfaceVariant = DouyinDarkSurface,
    onSurfaceVariant = DouyinDarkOnSurfaceVariant,
    outline = DouyinDarkOutline,
    error = Color(0xFFFF5252),
    onError = Color.White
)

private val ClassicLightColorScheme = lightColorScheme(
    primary = ClassicLightPrimary,
    onPrimary = Color.White,
    primaryContainer = ClassicLightSurface,
    onPrimaryContainer = ClassicLightOnSurface,
    secondary = ClassicLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = ClassicLightSurface,
    onSecondaryContainer = ClassicLightOnSurface,
    background = ClassicLightBg,
    onBackground = ClassicLightOnSurface,
    surface = ClassicLightSurface,
    onSurface = ClassicLightOnSurface,
    surfaceVariant = ClassicLightSurface,
    onSurfaceVariant = ClassicLightOnSurfaceVariant,
    outline = ClassicLightOutline,
    error = Color(0xFFD32F2F),
    onError = Color.White
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkPrimary,
    onPrimary = Color.White,
    primaryContainer = CyberpunkSurface,
    onPrimaryContainer = Color.White,
    secondary = CyberpunkSecondary,
    onSecondary = Color.Black,
    secondaryContainer = CyberpunkSurface,
    onSecondaryContainer = Color.White,
    background = CyberpunkBg,
    onBackground = CyberpunkOnSurface,
    surface = CyberpunkSurface,
    onSurface = CyberpunkOnSurface,
    surfaceVariant = CyberpunkSurface,
    onSurfaceVariant = CyberpunkOnSurfaceVariant,
    outline = CyberpunkOutline,
    error = Color(0xFFFF0D55),
    onError = Color.White
)

private val ForestZenColorScheme = lightColorScheme(
    primary = ForestZenPrimary,
    onPrimary = Color.White,
    primaryContainer = ForestZenSurface,
    onPrimaryContainer = ForestZenOnSurface,
    secondary = ForestZenSecondary,
    onSecondary = Color.White,
    secondaryContainer = ForestZenSurface,
    onSecondaryContainer = ForestZenOnSurface,
    background = ForestZenBg,
    onBackground = ForestZenOnSurface,
    surface = ForestZenSurface,
    onSurface = ForestZenOnSurface,
    surfaceVariant = ForestZenSurface,
    onSurfaceVariant = ForestZenOnSurfaceVariant,
    outline = ForestZenOutline,
    error = Color(0xFFC62828),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    preset: AppThemePreset = AppThemePreset.DOUYIN_DARK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (preset) {
        AppThemePreset.DOUYIN_DARK -> DouyinDarkColorScheme
        AppThemePreset.CLASSIC_LIGHT -> ClassicLightColorScheme
        AppThemePreset.CYBERPUNK -> CyberpunkColorScheme
        AppThemePreset.FOREST_ZEN -> ForestZenColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
