package io.karpilabs.simplemp3.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import io.karpilabs.simplemp3.data.prefs.ThemeMode

private val CarNightScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = NightBlack,
    primaryContainer = AccentTealDim,
    onPrimaryContainer = TextPrimary,
    secondary = AccentViolet,
    onSecondary = NightBlack,
    secondaryContainer = DeepViolet,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentCoral,
    onTertiary = NightBlack,
    background = NightBlack,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = NightCard,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    outlineVariant = NightElevated,
    error = AccentCoral,
    onError = TextPrimary,
    inverseSurface = TextPrimary,
    inverseOnSurface = NightBlack,
    inversePrimary = AccentTealDim,
    scrim = Color.Black.copy(alpha = 0.6f)
)

private val CarDayScheme = lightColorScheme(
    primary = AccentTealDim,
    onPrimary = Color.White,
    primaryContainer = AccentTeal,
    onPrimaryContainer = DayTextPrimary,
    secondary = AccentViolet,
    onSecondary = Color.White,
    secondaryContainer = DayElevated,
    onSecondaryContainer = DayTextPrimary,
    tertiary = AccentCoral,
    onTertiary = Color.White,
    background = DayBackground,
    onBackground = DayTextPrimary,
    surface = DaySurface,
    onSurface = DayTextPrimary,
    surfaceVariant = DayCard,
    onSurfaceVariant = DayTextSecondary,
    outline = DayTextMuted,
    outlineVariant = DayElevated,
    error = AccentCoral,
    onError = Color.White,
    inverseSurface = DayTextPrimary,
    inverseOnSurface = DaySurface,
    inversePrimary = AccentTeal,
    scrim = Color.Black.copy(alpha = 0.4f)
)

/** Resolved surface/text tokens a screen can pull instead of hardcoding dark- or light-only colors. */
data class SimpleMP3Palette(
    val card: Color,
    val elevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val gradientStart: Color,
    val gradientMid: Color,
    val gradientEnd: Color
)

private val NightPalette = SimpleMP3Palette(
    card = NightCard,
    elevated = NightElevated,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    gradientStart = GradientStart,
    gradientMid = GradientMid,
    gradientEnd = GradientEnd
)

private val DayPalette = SimpleMP3Palette(
    card = DayCard,
    elevated = DayElevated,
    textPrimary = DayTextPrimary,
    textSecondary = DayTextSecondary,
    textMuted = DayTextMuted,
    gradientStart = DayGradientStart,
    gradientMid = DayGradientMid,
    gradientEnd = DayGradientEnd
)

val LocalSimpleMP3Palette = androidx.compose.runtime.staticCompositionLocalOf { NightPalette }

@Composable
fun SimpleMP3Theme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (darkTheme) CarNightScheme else CarDayScheme
    val palette = if (darkTheme) NightPalette else DayPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalSimpleMP3Palette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
