package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * §ui-lecteur-v2 — refonte du thème par défaut façon lecteur moderne :
 *  - voile DÉGRADÉ (pas un bloc uni) sur le bandeau du bas ;
 *  - hiérarchie : titre + sous-titre en haut, temps collés à la barre ;
 *  - boutons transparents, chips pilulaires en verre, curseur avec halo.
 */
object DefaultTheme : PlayerTheme {
    override val id = "default"
    override val label = "Défaut"
    override val accent = Color(0xFF00BCD4)
    override val scrim = 0f
    override val playSize = 64

    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Box(
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent),
                    ),
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinIconButton(Icons.Default.ArrowBack, "Retour", actions.onBack, size = 44, iconSize = 22)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        state.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.subtitle.isNotBlank()) {
                        Text(
                            state.subtitle,
                            color = Color.White.copy(alpha = 0.70f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                SkinIconButton(Icons.Default.SkipPrevious, "Épisode précédent", actions.onPrev,
                    enabled = state.hasPrev, size = 44, iconSize = 22)
                SkinIconButton(Icons.Default.SkipNext, "Épisode suivant", actions.onNext,
                    enabled = state.hasNext, size = 44, iconSize = 22)
                SkinIconButton(Icons.Default.Lock, "Verrouiller", actions.onToggleLock, size = 44, iconSize = 20)
            }
        }
    }

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkinIconButton(Icons.Default.Replay10, "Reculer de 10 secondes", { actions.onJumpRelative(-10) },
                size = 48, iconSize = 28)
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, playSize)
            SkinIconButton(Icons.Default.Forward10, "Avancer de 10 secondes", { actions.onJumpRelative(10) },
                size = 48, iconSize = 28)
        }
    }

    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Box(
            modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)),
                    ),
                ),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                PlayerBarContent(state, actions, accent, top = false)
            }
        }
    }
}

/** Ligne des sauts + réglages : chips pilulaires, icônes transparentes. */
@Composable
internal fun SkipAndSettings(state: PlayerControlsState, actions: PlayerControlsActions, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (state.megaSkipLeft) SkinChip("+" + state.megaSkipSeconds + "s", false, accent, actions.onMegaJump)
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.activeSkip?.let {
                SkinChip(
                    "Passer " + it.type.label +
                        (state.skipCountdown?.let { n -> " (" + n + ")" } ?: ""),
                    true, accent, actions.onSkipSegment,
                )
            }
            state.customSkips.filter { it.enabled }.forEach { button ->
                SkinChip(button.label, false, accent, { actions.onCustomSkip(button) })
            }
        }
        if (!state.megaSkipLeft) SkinChip("+" + state.megaSkipSeconds + "s", false, accent, actions.onMegaJump)
        SkinIconButton(Icons.Default.Settings, "Paramètres du lecteur", actions.onOpenMore, size = 44, iconSize = 20)
        SkinIconButton(Icons.Default.Info, "Informations techniques", actions.onToggleStats, size = 44, iconSize = 20)
    }
}

/** Temps de part et d'autre, actions utiles regroupées (vitesse, ST, playlist, plein écran). */
@Composable
internal fun TimeAndActions(state: PlayerControlsState, actions: PlayerControlsActions, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            fmtTime(state.dragFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.weight(1f))
        SkinChip(state.speed.toString() + "x", state.speed != 1f, accent, actions.onCycleSpeed)
        SkinIconButton(Icons.Default.ClosedCaption, "Sous-titres", actions.onOpenSubtitles, size = 44, iconSize = 22)
        SkinIconButton(Icons.Default.PlaylistPlay, "Playlist", actions.onOpenPlaylist, size = 44, iconSize = 22)
        SkinIconButton(Icons.Default.Fullscreen, "Orientation plein écran", actions.onToggleOrientation, size = 44, iconSize = 22)
        Spacer(Modifier.weight(1f))
        Text(
            fmtTime(state.durationMs),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** One shared implementation: each section is rendered on exactly one side. */
@Composable
internal fun PlayerBarContent(state: PlayerControlsState, actions: PlayerControlsActions, accent: Color, top: Boolean) {
    val placement = PlayerBarPlacement(state.progressOnTop, state.toolsOnTop)
    if (placement.showsTools(top)) SkipAndSettings(state, actions, accent)
    if (placement.showsProgress(top)) SkinSeekBar(state, actions, accent, showTimes = false)
    if (placement.showsTools(top)) TimeAndActions(state, actions, accent)
}
