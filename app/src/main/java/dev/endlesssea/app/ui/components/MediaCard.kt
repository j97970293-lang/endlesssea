package dev.endlesssea.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImageContent
import dev.endlesssea.app.EsImagePlaceholder
import dev.endlesssea.app.EsImages
import dev.endlesssea.app.ui.search.SearchItemUi

/**
 * Carte média — quatre styles réglables (page Interface, capture AnyMEX) :
 *
 *  * `saikou`         : affiche verticale nette, note en pastille en bas à droite, titre dessous ;
 *  * `exotic`         : carte bordée, ombre colorée (halo) et bandeau d'action vif ;
 *  * `minimal_exotic` : carte bordée, titre sur dégradé bas, sans bandeau coloré ;
 *  * `modern`         : affiche pleine, titre en surimpression sur un dégradé sombre.
 *
 * L'arrondi suit le multiplicateur « Card Roundness » et l'appui joue une
 * animation d'échelle quand « Card Animation » est actif.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MediaCard(item: SearchItemUi, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val style by UiTuning.cardStyle.collectAsState()
    val roundnessMult by UiTuning.roundness.collectAsState()
    val glowMult by UiTuning.glow.collectAsState()
    val animate by UiTuning.cardAnimation.collectAsState()

    val corner = (18.dp * roundnessMult.coerceIn(0f, 5f)).coerceIn(0.dp, 48.dp)
    val shape = RoundedCornerShape(corner)
    val accent = MaterialTheme.colorScheme.primary

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (animate && pressed) 0.94f else 1f,
        label = "cardPress",
    )

    Column(
        modifier = Modifier
            .width(130.dp)
            .scale(scale)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        var poster = Modifier
            .fillMaxWidth()
            .height(185.dp)
        if (style == "exotic" && glowMult > 0f) {
            poster = poster.shadow(
                elevation = (6.dp * glowMult).coerceAtMost(24.dp),
                shape = shape,
                ambientColor = accent,
                spotColor = accent,
            )
        }
        poster = poster
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
        // §bordures : liseré d'affiche proportionnel au réglage global
        val borderStrength = UiTuning.borders.collectAsState().value / 100f
        if (style == "exotic" || style == "minimal_exotic") {
            val bs = borderStrength
            if (bs > 0.01f) {
                poster = poster.border(BorderStroke(1.dp, accent.copy(alpha = 0.55f * bs)), shape)
            }
        }

        Box(poster) {
            coil.compose.SubcomposeAsyncImage(
                // §4 : URL purgée (null → placeholder bleu, jamais de plantage) + crop
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(EsImages.safeImageUrl(item.posterUrl))
                    // §4-placeholder : mêmes clés mémoire → pas de flash vide
                    // quand on passe de la grille à la fiche (Coil réutilise l'image).
                    .memoryCacheKey(item.posterUrl)
                    .crossfade(180)
                    .build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(185.dp),
            ) {
                when (painter.state) {
                    is coil.compose.AsyncImagePainter.State.Error ->
                        EsImagePlaceholder(tint = Color(0xFFB3261E), modifier = Modifier.fillMaxWidth().height(185.dp))
                    is coil.compose.AsyncImagePainter.State.Loading,
                    is coil.compose.AsyncImagePainter.State.Empty ->
                        EsImagePlaceholder(tint = Color(0xFF2A5F8F), modifier = Modifier.fillMaxWidth().height(185.dp))
                    else -> SubcomposeAsyncImageContent()
                }
            }

            // ---- Langues annoncées par la source (toujours en haut à gauche)
            val langBadge = when {
                item.audioLangs.any { it.equals("vf", ignoreCase = true) } -> "VF"
                item.audioLangs.any { it.equals("vostfr", ignoreCase = true) } -> "VOSTFR"
                else -> null
            }
            if (langBadge != null) {
                Pill(langBadge, Modifier.align(Alignment.TopStart).padding(6.dp), accent)
            }

            // ---- Note : pastille bas-droite (Saikou) ou haut-droite (autres styles)
            item.rating?.let { note ->
                val align = if (style == "saikou") Alignment.BottomEnd else Alignment.TopEnd
                Pill("%.1f".format(note), Modifier.align(align).padding(6.dp), accent)
            }

            // ---- Titre en surimpression (Modern / Minimal Exotic) + bandeau vif (Exotic)
            if (style != "saikou") {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(if (style == "exotic") 56.dp else 64.dp)
                        .background(
                            Brush.verticalGradient(
                                if (style == "exotic") {
                                    listOf(Color.Transparent, accent.copy(alpha = 0.92f))
                                } else {
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                },
                            ),
                        ),
                ) {
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (style == "exotic") Color.Black else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.subtitle?.takeIf { style != "minimal_exotic" }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (style == "exotic") Color.Black.copy(alpha = 0.75f)
                                else Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // ---- Saikou : titre sobre sous l'affiche
        if (style == "saikou") {
            Text(
                item.title,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Petite pastille sombre lisible sur n'importe quelle affiche. */
@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, accent: Color) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}
