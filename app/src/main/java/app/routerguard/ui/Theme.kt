package app.routerguard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OnlineGreen = Color(0xFF3DD68C)
val WarnAmber = Color(0xFFF5B342)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DA8FF),
    onPrimary = Color(0xFF0A1330),
    primaryContainer = Color(0xFF1B2754),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF9AA7C7),
    onSecondary = Color(0xFF111827),
    secondaryContainer = Color(0xFF252E45),
    onSecondaryContainer = Color(0xFFD9E0F5),
    tertiary = Color(0xFF4DD0E1),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF0B0E14),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1C2330),
    onSurfaceVariant = Color(0xFFA3AEC2),
    surfaceContainerLowest = Color(0xFF080A10),
    surfaceContainerLow = Color(0xFF121722),
    surfaceContainer = Color(0xFF161C28),
    surfaceContainerHigh = Color(0xFF1C2330),
    surfaceContainerHighest = Color(0xFF232B3A),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2B0A0A),
    errorContainer = Color(0xFF4A1C1C),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6B7690),
    outlineVariant = Color(0xFF2A3345),
)

private val Light = lightColorScheme(
    primary = Color(0xFF3F51D8),
    primaryContainer = Color(0xFFE0E4FF),
    onPrimaryContainer = Color(0xFF111B5C),
    secondaryContainer = Color(0xFFE3E6F6),
    tertiary = Color(0xFF0E7490),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFF6F7FB),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDEFF7),
    surfaceContainerHigh = Color(0xFFE6E9F4),
)

/** 0 = follow system, 1 = dark (default), 2 = light. */
@Composable
fun resolveDark(mode: Int): Boolean = when (mode) {
    1 -> true
    2 -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun RouterGuardTheme(mode: Int, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (resolveDark(mode)) Dark else Light, content = content)
}
