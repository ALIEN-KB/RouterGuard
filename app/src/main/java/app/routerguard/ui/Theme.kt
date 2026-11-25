package app.routerguard.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val OnlineGreen = Color(0xFF2E9E5B)

private val Light = lightColorScheme(
    primary = Color(0xFF4338CA),
    secondary = Color(0xFF5B5F97),
    tertiary = Color(0xFF0E7490),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    secondary = Color(0xFFC3C5EE),
    tertiary = Color(0xFF67D4EE),
)

@Composable
fun RouterGuardTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
