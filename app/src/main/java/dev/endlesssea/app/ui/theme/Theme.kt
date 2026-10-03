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
import dev.endlesssea.app.di.AppPrefs

// Palette « mer profonde » — bleu profond / cyan vif
private val SeaDark = darkColorScheme(
    primary = Color(0xFF7AD8FF),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D66),
    onPrimaryContainer = Color(0xFFBFE9FF),
    secondary = Color(0xFF8CD6C3),
    onSecondary = Color(0xFF00382D),
    tertiary = Color(0xFF16C2C2),
    surface = Color(0xFF0E1518),
    onSurface = Color(0xFFDDE4E7),
    surfaceVariant = Color(0xFF16222A),
    onSurfaceVariant = Color(0xFF8FA2AE),
    background = Color(0xFF0B1114),
)

// AMOLED : fonds en noir pur (économie d'écran, plus beau dégradé flottant)
private val SeaAmoled = darkColorScheme(
    primary = Color(0xFF7AD8FF),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D66),
    onPrimaryContainer = Color(0xFFBFE9FF),
    secondary = Color(0xFF8CD6C3),
    onSecondary = Color(0xFF00382D),
    tertiary = Color(0xFF16C2C2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE6EBEE),
    surfaceVariant = Color(0xFF0B0B0B),
    onSurfaceVariant = Color(0xFF94A5B1),
    background = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF050505),
)

private val SeaLight = lightColorScheme(
    primary = Color(0xFF006684),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFE9FF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF1B6B5B),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF0E7C8C),
    surface = Color(0xFFFBFDFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDCE4E8),
    background = Color(0xFFF6FAFB),
)

/**
 * Thème global piloté par les préférences :
 * [AppPrefs.THEME_SYSTEM|THEME_LIGHT|THEME_DARK|THEME_AMOLED].
 */
@Composable
fun EndlessSeaTheme(
    themeMode: Int = AppPrefs.THEME_SYSTEM,
    dynamicColor: Boolean = false,   // verre + AMOLED demandent la palette fixe « mer »
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppPrefs.THEME_LIGHT -> false
        AppPrefs.THEME_DARK, AppPrefs.THEME_AMOLED -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colors = when {
        themeMode == AppPrefs.THEME_AMOLED -> SeaAmoled
        dynamicColor && android.os.Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> SeaDark
        else -> SeaLight
    }
    MaterialTheme(colorScheme = colors, content = content)
}
