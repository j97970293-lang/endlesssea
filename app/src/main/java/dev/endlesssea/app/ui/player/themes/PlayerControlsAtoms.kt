@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.skip.CustomSkipButton
import dev.endlesssea.app.skip.SkipSegment

/**
 * §ui-lecteur-v2 : briques visuelles repensées façon lecteurs modernes
 * (YouTube / Netflix / MX) —
 *  - boutons icône TRANSPARENTS par défaut (plus de pastilles opaques) ;
 *  - bouton lecture en verre avec anneau discret ;
 *  - barre fine, buffer visible, curseur avec halo blanc ;
 *  - chips pilulaires (pill) en verre fumé.
 * Les signatures publiques sont inchangées : les thèmes et PlayerActivity
 * compilent sans modification.
 */

/** « 1:02:03 » / « 12:34 » — jamais de zéro inutile. */
fun fmtTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Verre fumé partagé : noir translucide, jamais opaque. */
internal val GlassDark: Color = Color(0x59000000)
internal val Hairline: Color = Color(0x2EFFFFFF)

/** Bouton d'icône commun — transparent par défaut (façon YouTube), verre au besoin. */
@Composable
fun SkinIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    size: Int = 40,
    iconSize: Int = 20,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    background: Color = Color.Transparent,
    border: Color? = null,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .then(if (background != Color.Transparent) Modifier.background(background) else Modifier)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

/** Bouton lecture central : anneau de verre, icône blanche nette. */
@Composable
fun SkinPlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    accent: Color,
    size: Int,
    filled: Boolean = false,
    square: Boolean = false,
    iconColor: Color = Color.White,
) {
    val shape = if (square) RoundedCornerShape(6.dp) else CircleShape
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(if (filled) accent else Color(0x66000000))
            .then(
                if (!filled) {
                    Modifier.border(1.5.dp, Color.White.copy(alpha = 0.55f), shape)
                } else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (playing) "Pause" else "Lecture",
            tint = if (filled) Color.Black else iconColor,
            modifier = Modifier.size(((size * 0.58f).toInt()).dp),
        )
    }
}

/**
 * Barre de progression moderne : piste fine grisée, buffer visible,
 * progression accent, curseur avec anneau blanc (halo façon YouTube).
 */
@Composable
fun SkinSeekBar(
    state: PlayerControlsState,
    actions: PlayerControlsActions,
    accent: Color,
    modifier: Modifier = Modifier,
    thickness: Int = state.progressThickness,
    rounded: Boolean = state.progressRounded,
    showTimes: Boolean = true,
    remaining: Boolean = false,
    timeColor: Color = Color.White,
    thumbSize: Int = state.thumbSize,
) {
    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = (state.dragFraction ?: (state.positionMs.toFloat() / duration)).coerceIn(0f, 1f)
    val shape: Shape = if (rounded) RoundedCornerShape(50) else RoundedCornerShape(1.dp)

    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTimes) {
            Text(
                fmtTime(state.dragFraction?.let { (it * duration).toLong() } ?: state.positionMs),
                color = timeColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = fraction,
            onValueChange = { actions.onDrag(it) },
            onValueChangeFinished = { actions.onDragFinished(fraction) },
            enabled = state.durationMs > 0,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            thumb = {
                Box(
                    Modifier
                        .size((if (state.autoHideThumb && state.dragFraction == null) 0 else thumbSize).dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                )
            },
            track = { sliderState ->
                val frac = (sliderState.value - sliderState.valueRange.start) /
                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(maxOf(thickness, state.bufferThickness).dp)
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        Modifier.align(Alignment.CenterStart)
                            .fillMaxWidth((state.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f))
                            .height(state.bufferThickness.dp)
                            .background(Color.White.copy(alpha = 0.38f)),
                    )
                    Box(
                        Modifier.align(Alignment.CenterStart)
                            .fillMaxWidth(frac.coerceIn(0f, 1f))
                            .height(thickness.dp)
                            .clip(shape)
                            .background(accent),
                    )
                }
            },
        )
        if (showTimes) {
            Text(
                if (remaining) "-" + fmtTime(duration - state.positionMs) else fmtTime(duration),
                color = timeColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Puce d'action pilulaire en verre fumé — active = accent plein, texte noir. */
@Composable
fun SkinChip(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    border: Boolean = true,
    textColor: Color = Color.White,
) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .then(
                when {
                    active -> Modifier.background(accent)
                    else -> Modifier.background(GlassDark)
                },
            )
            .then(
                if (!active && border) Modifier.border(1.dp, Hairline, shape) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon, null,
                tint = if (active) Color.Black else textColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            color = if (active) Color.Black else textColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Libellé du cadrage courant (utilisé par les thèmes détaillés). */
fun zoomLabel(mode: Int, manualZoom: Boolean = false): String = when {
    manualZoom -> "Manuel"
    mode == 0 -> "Ajuster"
    mode == 1 -> "Recadrer"
    mode == 2 -> "Étirer"
    mode == 3 -> "Fond flou"
    mode == 4 -> "16:9"
    mode == 5 -> "4:3"
    mode == 6 -> "21:9"
    mode == 7 -> "18:9"
    mode == 8 -> "2.35:1"
    else -> "Ajuster"
}
