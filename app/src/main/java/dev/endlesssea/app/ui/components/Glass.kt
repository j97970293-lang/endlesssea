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
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val tint = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.65f),
                    if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.42f),
                ),
            ),
        )
        .background(tint)
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
