package dev.endlesssea.app.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * « Verre » façon glassmorphism compatible Android 8+ :
 * fond translucide + bord lumineux 1 dp (+ halo léger). Le vrai flou d'arrière-plan
 * est appliqué uniquement sur Android 12+ via [Modifier.blur] sur le contenu d'arrière-plan.
 */
@Composable
fun Modifier.glass(
    cornerRadius: Dp = 20.dp,
    dark: Boolean = isSystemInDarkTheme(),
): Modifier {
    // Réglages « Liquid Mode » : couche translucide et intensité de flou (simulé
    // par assombrissement du fond, compatible Android 8+).
    val overlay = UiTuning.glassOverlay.collectAsState().value / 100f
    val scrim = UiTuning.glassScrim.collectAsState().value / 100f
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val scale = if (dark) 1f else 3f   // le blanc ressort plus sur thème clair
    val baseHi = (overlay * scale).coerceIn(0f, 1f)
    val baseLo = (overlay * scale * 0.45f).coerceIn(0f, 1f)
    val tintArgb = UiTuning.glassTint.collectAsState().value
    // §verre-liquide : intensité du reflet « lentille » (0..1)
    val liquid = UiTuning.liquid.collectAsState().value / 100f
    return if (tintArgb != null) {
        // §24 — « tout teinté » : couche couleur RICHE par-dessus un voile sombre
        // qui garantit le contraste du texte (blanc ≥ 4.5:1 en thème sombre).
        val tint = Color(tintArgb)
        val darkVeil = (scrim * if (dark) 0.65f else 0.18f).coerceAtLeast(if (dark) 0.42f else 0.14f)
        this
            .clip(shape)
            .background(Color.Black.copy(alpha = darkVeil))
            .background(
                Brush.verticalGradient(
                    listOf(
                        tint.copy(alpha = if (dark) 0.62f else 0.44f),
                        tint.copy(alpha = if (dark) 0.30f else 0.22f),
                        tint.copy(alpha = if (dark) 0.38f else 0.26f),
                    ),
                ),
            )
            .background(
                // Reflet « liquide » : une lumière blanche fine en haut à gauche
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(400f, 400f),
                ),
            )
            .background(
                // §verre-liquide : lentille lumineuse (comme une goutte) — réglable
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.34f * liquid), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(70f, 50f),
                    radius = 620f,
                ),
            )
            .background(
                // contre-reflet doux en bas à droite (profondeur « liquide »)
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.14f * liquid), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset.Infinite,
                    radius = 700f,
                ),
            )
    } else {
        this
            .clip(shape)
            .background(Color.Black.copy(alpha = scrim * if (dark) 0.55f else 0.20f))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = baseHi), Color.White.copy(alpha = baseLo)),
                ),
            )
            .background(
                // §verre-liquide : lentille même sans teinte
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.22f * liquid), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(70f, 50f),
                    radius = 620f,
                ),
            )
    }
}

/** Carte en verre : Surface translucide + bord lumineux (teinté si variante §24). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    // §multiplicateurs : le réglage « rayon » de la page Interface agit vraiment
    val radiusMult = UiTuning.radius.collectAsState().value
    val shape = RoundedCornerShape(cornerRadius * radiusMult.coerceIn(0.2f, 3f))
    val tintArgb = UiTuning.glassTint.collectAsState().value
    // §bordures : « trop de bordures » — la force du liseré est réglable et, à 0,
    // la carte n'en dessine plus du tout (verre franc, sans cadre dans le cadre).
    val strength = UiTuning.borders.collectAsState().value / 100f
    val edge = (tintArgb?.let { Color(it) } ?: Color.White).copy(alpha = 0.55f * strength)
    Surface(
        modifier = modifier.glass(cornerRadius * radiusMult.coerceIn(0.2f, 3f)),
        shape = shape,
        color = Color.Transparent,
        border = if (strength <= 0.01f) null else BorderStroke(1.dp, edge),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}

/** Floutage sécurisé : inopérant avant Android 12 (RenderEffect). */
fun Modifier.blurIfSupported(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= 31) this.blur(radius) else this

/** §fond-flou : flou utilisable comme Modifier (inopérant avant Android 12). */
fun blurModifier(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= 31) Modifier.blur(radius) else Modifier
