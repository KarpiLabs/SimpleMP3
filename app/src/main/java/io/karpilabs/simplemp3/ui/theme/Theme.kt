package io.karpilabs.simplemp3.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CarNightScheme = darkColorScheme(
    primary = AccentTeal,
    onPrimary = NightBlack,
    primaryContainer = AccentTealDim,
    onPrimaryContainer = TextPrimary,
    secondary = AccentViolet,
    onSecondary = NightBlack,
    secondaryContainer = NightElevated,
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

@Composable
fun SimpleMP3Theme(
    darkTheme: Boolean = true, // always car-night for that AAA in-car feel
    content: @Composable () -> Unit
) {
    val colorScheme = CarNightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = NightBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
