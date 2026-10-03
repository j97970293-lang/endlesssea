package dev.endlesssea.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// "Sea" palette — deep blue/teal
private val SeaDark = darkColorScheme(
    primary = Color(0xFF7AD8FF),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D66),
    onPrimaryContainer = Color(0xFFBFE9FF),
    secondary = Color(0xFF8CD6C3),
    onSecondary = Color(0xFF00382D),
    surface = Color(0xFF0E1518),
    onSurface = Color(0xFFDDE4E7),
    surfaceVariant = Color(0xFF16222A),
    background = Color(0xFF0B1114),
)

private val SeaLight = lightColorScheme(
    primary = Color(0xFF006684),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFE9FF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF1B6B5B),
    onSecondary = Color(0xFFFFFFFF),
    surface = Color(0xFFFBFDFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDCE4E8),
    background = Color(0xFFF6FAFB),
)

@Composable
fun EndlessSeaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,   // Material You on API 31+, palette fallback below
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= 31 ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> SeaDark
        else -> SeaLight
    }
    MaterialTheme(colorScheme = colors, content = content)
}
