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
    // Variante §24 : teinte dominante (Glass Blue/Green/…) si définie — sinon blanc neutre.
    val tintArgb = UiTuning.glassTint.collectAsState().value
    val glow = tintArgb?.let { Color(it) } ?: Color.White
    return this
        .clip(shape)
        .background(Color.Black.copy(alpha = scrim * if (dark) 0.55f else 0.20f))
        .background(
            Brush.verticalGradient(
                listOf(
                    glow.copy(alpha = if (tintArgb != null) (baseHi * 1.4f).coerceAtMost(0.9f) else baseHi),
                    glow.copy(alpha = baseLo),
                ),
            ),
        )
}

/** Carte en verre : Surface translucide + bord lumineux, prête à l'emploi. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier.glass(cornerRadius),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.padding(contentPadding), content = content)
    }
}

/** Floutage sécurisé : inopérant avant Android 12 (RenderEffect). */
fun Modifier.blurIfSupported(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= 31) this.blur(radius) else this
