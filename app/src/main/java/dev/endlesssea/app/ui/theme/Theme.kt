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

// Palette inspiration Anymex : bleu nuit indigo + pervenche (periwinkle)
private val SeaDark = darkColorScheme(
    primary = Color(0xFFB9C1FF),
    onPrimary = Color(0xFF26315F),
    primaryContainer = Color(0xFF3D4779),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Color(0xFFC0C2E8),
    onSecondary = Color(0xFF2A2E4A),
    tertiary = Color(0xFF7AE0E0),
    surface = Color(0xFF0B0E1A),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF141830),
    onSurfaceVariant = Color(0xFF8D93B8),
    background = Color(0xFF080A14),
)

// AMOLED : noir pur + cartes bleu nuit — la référence visuelle (par défaut)
private val SeaAmoled = darkColorScheme(
    primary = Color(0xFFAFB8FF),
    onPrimary = Color(0xFF273162),
    primaryContainer = Color(0xFF3E4880),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Color(0xFFC0C2E8),
    onSecondary = Color(0xFF242847),
    tertiary = Color(0xFF7AE0E0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE4E3EE),
    surfaceVariant = Color(0xFF0C0F1E),
    onSurfaceVariant = Color(0xFF8D93B8),
    background = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF0C0F1E),
    surfaceContainer = Color(0xFF070810),
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
    accentArgb: Long = 0xFFB9C1FF,   // accent primaire choisi dans les réglages
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
    val tinted = if (accentArgb == 0L) colors else colors.copy(
        primary = Color(accentArgb),
        primaryContainer = Color(accentArgb).copy(alpha = 0.22f),
    )
    MaterialTheme(colorScheme = tinted, content = content)
}
