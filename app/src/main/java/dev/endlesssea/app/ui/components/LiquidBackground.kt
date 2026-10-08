package dev.endlesssea.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * §verre-liquide-fond : le fond AMOLED/sombre devient une surface de « verre liquide » —
 * trois nappes lumineuses aux couleurs de l'accent dérivent lentement (InfiniteTransition,
 * 26 s / 34 s / 47 s, linéaires), estompées par des dégradés radiaux (inutile d'un vrai
 * blur GPU : le fondu du dégradé produit déjà ce verre — aucun `Modifier.blur`,
 * comportement identique sur toutes API ≥ 26).
 *
 * Intensité = [UiTuning.liquid] (0 → fond uni, 100 → halo accent à ~15 % d'alpha).
 * Peu cher : 3 dégradés vectoriels, aucune image, aucune recomposition par frame
 * (Canvas redessiné avec les phases animées).
 */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val liquid by UiTuning.liquid.collectAsState()
    val bloom by UiTuning.bloom.collectAsState()
    val base = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.secondary
    val third = MaterialTheme.colorScheme.tertiary

    if (liquid <= 0) {
        // Verre liquide désactivé : fond de thème uni (AMOLED → noir pur).
        Canvas(modifier.fillMaxSize()) { drawRect(base) }
        return
    }

    // §anymex-theme : Bloom amplifie les halos (~1.6×) sans changer la durée.
    val strength = (liquid / 100f) * if (bloom) 1.6f else 1f
    val p1 = dev.endlesssea.app.ui.motion.rememberMotionPhase(26_000, 0.2f)
    val p2 = dev.endlesssea.app.ui.motion.rememberMotionPhase(34_000, 0.4f)
    val p3 = dev.endlesssea.app.ui.motion.rememberMotionPhase(47_000, 0.6f)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val maxDim = maxOf(w, h)
        drawRect(base)

        fun blob(color: Color, alpha: Float, cxFr: Float, cyFr: Float, rFr: Float) {
            val c = color.copy(alpha = (alpha * strength).coerceIn(0f, 1f))
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(0f to c, 1f to c.copy(alpha = 0f)),
                    center = Offset(cxFr * w, cyFr * h),
                    radius = rFr * maxDim,
                ),
                radius = rFr * maxDim,
                center = Offset(cxFr * w, cyFr * h),
            )
        }

        val tau = 2f * PI.toFloat()
        // Nappe principale (accent) — grande orbite lente en haut
        blob(
            accent, 0.240f,
            0.30f + 0.28f * cos(p1.value * tau),
            0.24f + 0.18f * sin(p1.value * tau * 0.9f + 0.6f),
            0.72f,
        )
        // Nappe secondaire — contre-orbite au milieu
        blob(
            second, 0.176f,
            0.72f + 0.24f * cos(p2.value * tau + 2.1f),
            0.52f + 0.22f * sin(p2.value * tau * 1.1f),
            0.80f,
        )
        // Petite lueur tertiaire en bas — mouvement de houle
        blob(
            third, 0.152f,
            0.42f + 0.30f * sin(p3.value * tau * 0.8f),
            0.86f + 0.14f * cos(p3.value * tau + 4.0f),
            0.62f,
        )
    }
}
