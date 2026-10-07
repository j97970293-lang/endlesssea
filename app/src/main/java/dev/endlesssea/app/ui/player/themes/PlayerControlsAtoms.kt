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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.skip.CustomSkipButton
import dev.endlesssea.app.skip.SkipSegment

/** « 1:02:03 » / « 12:34 » — jamais de zéro inutile. */
fun fmtTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Accent effectif d'un thème (couleur de l'application si `null`). */
@Composable
fun PlayerTheme.accentColor(): Color = accent ?: MaterialTheme.colorScheme.primary

/** Bouton d'icône commun — rond, carré ou sans fond selon le thème. */
@Composable
fun SkinIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    size: Int = 40,
    iconSize: Int = 22,
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

/** Bouton lecture central : plein (accent) ou contour, rond ou carré. */
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
    val shape = if (square) RoundedCornerShape(4.dp) else CircleShape
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .then(if (filled) Modifier.background(accent) else Modifier)
            .then(
                if (!filled && accent.alpha > 0f && iconColor != accent) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), shape)
                } else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (playing) "Pause" else "Lecture",
            tint = if (filled) Color.Black else iconColor,
            modifier = Modifier.size(((size * 0.62f).toInt()).dp),
        )
    }
}

/** Bouton précédent / suivant (grisé si absent de la file). */
@Composable
fun SkinSkipButton(
    next: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = Color.White,
    size: Int = 52,
    iconSize: Int = 40,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size.dp)) {
        Icon(
            if (next) Icons.Filled.SkipNext else Icons.Filled.SkipPrevious,
            if (next) "Suivant" else "Précédent",
            tint = if (enabled) tint else tint.copy(alpha = 0.25f),
            modifier = Modifier.size(iconSize.dp),
        )
    }
}

/**
 * Ligne de temps partagée : Slider Material 3 avec piste et curseur aux couleurs
 * du thème. [remaining] affiche le temps restant (négatif) au lieu de la durée.
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
    thumbSize: Int = thickness + 8,
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
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Slider(
            value = fraction,
            onValueChange = { actions.onDrag(it) },
            onValueChangeFinished = { actions.onDragFinished(fraction) },
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            thumb = {
                Box(
                    Modifier
                        .size(thumbSize.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            },
            track = { sliderState ->
                val frac = (sliderState.value - sliderState.valueRange.start) /
                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(thickness.dp)
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(
                        Modifier
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
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Petite puce d'action (cadrage, vitesse, statut) avec ou sans contour. */
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
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .clip(shape)
            .then(
                when {
                    active -> Modifier.background(accent)
                    border -> Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), shape)
                    else -> Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon, null,
                tint = if (active) Color.Black else textColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
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
fun zoomLabel(mode: Int): String = when (mode) {
    0 -> "Contenir"
    1 -> "Remplir"
    else -> "Étirer"
}

/**
 * Bloc mégaskip partagé : pastille « +85 s », pastille du segment actif et
 * boutons personnalisés. Chaque thème choisit où le poser et avec quelles
 * marges (le comportement — saut, appui long = éditeur — reste commun).
 */
@Composable
fun MegaSkipCluster(
    state: PlayerControlsState,
    actions: PlayerControlsActions,
    accent: Color,
    modifier: Modifier = Modifier,
    pillTextColor: Color = Color.Black,
    customRowAbove: Boolean = true,
) {
    Box(modifier) {
        if (customRowAbove && state.customSkips.isNotEmpty()) {
            dev.endlesssea.app.ui.player.MegaskipRow(
                buttons = state.customSkips.filter { it.enabled },
                accent = accent,
                onJump = actions.onCustomSkip,
                onLongPress = actions.onOpenSkipEditor,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!state.megaSkipLeft) Spacer(Modifier.weight(1f))
            // Pastille du segment communautaire (intro / générique…)
            state.activeSkip?.let { segment ->
                dev.endlesssea.app.ui.player.SkipSegmentPill(
                    segment = segment,
                    countdown = state.skipCountdown,
                    accent = accent,
                    onClick = actions.onSkipSegment,
                )
                Spacer(Modifier.width(10.dp))
            }
            // Pastille « +N s » (saut personnalisé principal)
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(accent)
                    .clickable(onClick = actions.onMegaJump)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    "+${state.megaSkipSeconds} s",
                    color = pillTextColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (state.megaSkipLeft) Spacer(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        }
        if (!customRowAbove && state.customSkips.isNotEmpty()) {
            dev.endlesssea.app.ui.player.MegaskipRow(
                buttons = state.customSkips.filter { it.enabled },
                accent = accent,
                onJump = actions.onCustomSkip,
                onLongPress = actions.onOpenSkipEditor,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
            )
        }
    }
}

/** Rangée d'outils complète (verrou, orientation, cadrage, vitesse, filtres…). */
@Composable
fun AllTools(
    state: PlayerControlsState,
    actions: PlayerControlsActions,
    accent: Color,
    progressOnTop: Boolean,
    compact: Boolean = false,
    bordered: Boolean = false,
) {
    val tintActive = accent
    val shape = if (bordered) RoundedCornerShape(10.dp) else CircleShape
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkinIconButton(
            if (state.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            "Verrouiller",
            actions.onToggleLock,
            size = if (compact) 34 else 40,
            iconSize = if (compact) 18 else 22,
        )
        SkinIconButton(
            Icons.Filled.ScreenRotation,
            "Orientation",
            actions.onToggleOrientation,
            size = if (compact) 34 else 40,
            iconSize = if (compact) 18 else 22,
        )
        // §fit : contenir → remplir → étirer
        Row(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = actions.onCycleZoom)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ZoomIn, "Cadrage",
                tint = if (state.zoomMode == 0) Color.White else tintActive,
                modifier = Modifier.size((if (compact) 18 else 22).dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                zoomLabel(state.zoomMode),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            "${state.speed}×",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = actions.onCycleSpeed)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        SkinIconButton(
            Icons.Filled.Tune, "Filtres vidéo",
            actions.onOpenFilters,
            tint = if (state.filterActive) tintActive else Color.White,
            size = if (compact) 34 else 40,
            shape = shape,
        )
        SkinIconButton(
            Icons.Filled.Info, "Statistiques de lecture",
            actions.onToggleStats,
            tint = if (state.statsVisible) tintActive else Color.White,
            size = if (compact) 34 else 40,
            shape = shape,
        )
        SkinIconButton(
            Icons.Filled.Refresh, "Rafraîchir les segments",
            actions.onRefreshSkip,
            tint = if (state.skipLoading) tintActive else Color.White,
            size = if (compact) 34 else 40,
            shape = shape,
        )
        SkinIconButton(
            Icons.Filled.MoreVert, "Plus",
            actions.onOpenMore,
            size = if (compact) 34 else 40,
            shape = shape,
        )
    }
}

/** Icône de piste sous-titre / audio, colorée quand elle est active. */
@Composable
fun TrackIcons(
    state: PlayerControlsState,
    actions: PlayerControlsActions,
    accent: Color,
    size: Int = 40,
    iconSize: Int = 22,
) {
    if (state.hasLinks) {
        SkinIconButton(
            Icons.Filled.HighQuality, "Qualité / serveur", actions.onOpenQuality,
            size = size, iconSize = iconSize,
        )
    }
    SkinIconButton(
        Icons.Filled.ClosedCaption, "Sous-titres", actions.onOpenSubtitles,
        tint = accent, size = size, iconSize = iconSize,
    )
    SkinIconButton(
        Icons.Filled.Audiotrack, "Piste audio", actions.onOpenAudio,
        size = size, iconSize = iconSize,
    )
}

/** Épingle l'épisode ou la source sous le titre, quand le thème le montre. */
@Composable
fun SubtitleLine(state: PlayerControlsState) {
    if (state.subtitle.isNotBlank()) {
        Text(
            state.subtitle,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
