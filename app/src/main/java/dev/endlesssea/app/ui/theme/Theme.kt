package dev.endlesssea.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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
    uiBrightness: Int = 0,           // §luminosite : éclaircit les surfaces sombres
    textOutline: Boolean = true,     // §lisibilite : ombre portée sur les textes
    dynamicColor: Boolean = false,   // verre + AMOLED demandent la palette fixe « mer »
    fontId: String = "system",       // police choisie dans Réglages (téléchargeable)
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
    // §accent-partout : l'accent choisi ne touchait que `primary` — d'où
    // l'impression « ma couleur n'apparaît pas ». Il colore maintenant toute
    // la palette (secondaire, tertiaire, conteneurs, teinte des surfaces).
    val accent = Color(accentArgb)
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF101014) else Color.White
    val accented = if (accentArgb == 0L) colors else colors.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.26f).compositeOver(colors.surface),
        onPrimaryContainer = if (dark) Color.White else Color(0xFF101014),
        secondary = accent.copy(alpha = 0.85f).compositeOver(colors.surface),
        onSecondary = onAccent,
        secondaryContainer = accent.copy(alpha = 0.18f).compositeOver(colors.surface),
        tertiary = accent,
        tertiaryContainer = accent.copy(alpha = 0.14f).compositeOver(colors.surface),
        surfaceTint = accent,
        inversePrimary = accent,
        outline = accent.copy(alpha = 0.40f).compositeOver(colors.outline),
    )
    // §luminosite : l'interface paraissait trop sombre — un réglage éclaircit
    // réellement les surfaces (0 = inchangé).
    val lift = (uiBrightness.coerceIn(0, 40)) / 100f
    val tinted = if (lift <= 0f || !dark) accented else accented.copy(
        background = Color.White.copy(alpha = lift * 0.55f).compositeOver(accented.background),
        surface = Color.White.copy(alpha = lift).compositeOver(accented.surface),
        surfaceVariant = Color.White.copy(alpha = lift * 1.2f).compositeOver(accented.surfaceVariant),
    )
    val family = fontFamilyFor(fontId)
    val base = if (family == null) androidx.compose.material3.Typography()
    else androidx.compose.material3.Typography().withFamily(family)
    // §lisibilite : fine ombre portée sur tous les textes — indispensable
    // au-dessus des affiches et des fonds clairs (« des choses qu'on ne voit pas »).
    // §lisibilite : le halo prend la couleur OPPOSÉE au texte — noir derrière un
    // texte clair, blanc derrière un texte sombre — pour que chaque mot ressorte,
    // y compris sur une affiche ou dans la barre de navigation.
    val haloDark = tinted.onSurface.luminance() > 0.5f
    val typography = if (!textOutline) base else base.withShadow(
        androidx.compose.ui.graphics.Shadow(
            color = (if (haloDark) Color.Black else Color.White).copy(alpha = 0.85f),
            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
            blurRadius = 5f,
        ),
    )
    MaterialTheme(colorScheme = tinted, typography = typography, content = content)
}

// ----------------------------------------------------------------- polices §37
@androidx.compose.ui.text.ExperimentalTextApi
private val esFontProvider = androidx.compose.ui.text.googlefonts.GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = dev.endlesssea.app.R.array.com_google_android_gms_fonts_certs,
)

/** Famille téléchargeable (fallback Roboto silencieux si indisponible : bestEffort). */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun fontFamilyFor(fontId: String): androidx.compose.ui.text.font.FontFamily? = when (fontId) {
    "outfit" -> androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.googlefonts.Font(
            googleFont = androidx.compose.ui.text.googlefonts.GoogleFont("Outfit"),
            fontProvider = esFontProvider,
        ),
    )
    "rubik" -> androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.googlefonts.Font(
            googleFont = androidx.compose.ui.text.googlefonts.GoogleFont("Rubik"),
            fontProvider = esFontProvider,
        ),
    )
    "lora" -> androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.googlefonts.Font(
            googleFont = androidx.compose.ui.text.googlefonts.GoogleFont("Lora"),
            fontProvider = esFontProvider,
        ),
    )
    else -> null
}

private fun androidx.compose.material3.Typography.withFamily(
    f: androidx.compose.ui.text.font.FontFamily,
) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

/** §lisibilite : applique une ombre portée à tous les styles de texte. */
private fun androidx.compose.material3.Typography.withShadow(
    shadow: androidx.compose.ui.graphics.Shadow,
): androidx.compose.material3.Typography = copy(
    displayLarge = displayLarge.copy(shadow = shadow),
    displayMedium = displayMedium.copy(shadow = shadow),
    displaySmall = displaySmall.copy(shadow = shadow),
    headlineLarge = headlineLarge.copy(shadow = shadow),
    headlineMedium = headlineMedium.copy(shadow = shadow),
    headlineSmall = headlineSmall.copy(shadow = shadow),
    titleLarge = titleLarge.copy(shadow = shadow),
    titleMedium = titleMedium.copy(shadow = shadow),
    titleSmall = titleSmall.copy(shadow = shadow),
    bodyLarge = bodyLarge.copy(shadow = shadow),
    bodyMedium = bodyMedium.copy(shadow = shadow),
    bodySmall = bodySmall.copy(shadow = shadow),
    labelLarge = labelLarge.copy(shadow = shadow),
    labelMedium = labelMedium.copy(shadow = shadow),
    labelSmall = labelSmall.copy(shadow = shadow),
)
