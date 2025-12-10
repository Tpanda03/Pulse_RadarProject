package com.group4.pulse.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Custom colors for radar theme
val RadarGreen = Color(0xFF00FF00)
val RadarAmber = Color(0xFFFFC107)
val RadarRed = Color(0xFFFF5722)
val DarkBackground = Color(0xFF0A0E27)
val DarkSurface = Color(0xFF1C2340)
val DarkSurfaceVariant = Color(0xFF2A3456)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E676),
    onPrimary = Color(0xFF003D1C),
    primaryContainer = Color(0xFF005227),
    onPrimaryContainer = Color(0xFF7FFF96),

    secondary = Color(0xFF4FC3F7),
    onSecondary = Color(0xFF003548),
    secondaryContainer = Color(0xFF004D67),
    onSecondaryContainer = Color(0xFFB3E5FC),

    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3900),
    onTertiaryContainer = Color(0xFFFFDDB3),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = DarkBackground,
    onBackground = Color(0xFFE1E3E9),

    surface = DarkSurface,
    onSurface = Color(0xFFE1E3E9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC2C7CF),

    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),

    inverseSurface = Color(0xFFE1E3E9),
    inverseOnSurface = Color(0xFF2E3137),
    inversePrimary = Color(0xFF006D35)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006D35),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7FFF96),
    onPrimaryContainer = Color(0xFF00210E),

    secondary = Color(0xFF006687),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBFE8FF),
    onSecondaryContainer = Color(0xFF001F2A),

    tertiary = Color(0xFF7C5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF271900),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF191C1A),

    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDDE5DD),
    onSurfaceVariant = Color(0xFF414942),

    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9C1),

    inverseSurface = Color(0xFF2E312E),
    inverseOnSurface = Color(0xFFF0F1ED),
    inversePrimary = Color(0xFF61DF7D)
)

@Composable
fun RadarDetectionTheme(
    darkTheme: Boolean = true, // Force dark theme for radar aesthetic
    dynamicColor: Boolean = false, // Disable dynamic color for consistent radar look
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

val Typography = Typography(
    // Use default Material3 typography
)