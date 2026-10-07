package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Thème **YouTube** — plat et minimal : pas de gros voile, barre rouge très
 * fine (4 dp) collée au bas de l'écran, rangée d'icônes compacte (play,
 * suivant, temps « 12:34 / 24:00 », ±10 s, vitesse, sous-titres, qualité,
 * rafraîchir, statistiques, plus), grand triangle central sans cercle.
 */
object YouTubeTheme : PlayerTheme {
    private val RED = Color(0xFFFF0033)
    override val id = "youtube"
    override val label = "YouTube"
    override val accent = RED
    override val scrim = 0.35f
    override val progressThickness = 4
    override val rounded = false
    override val playSize = 74

    @Composable
    override fun TopControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Row(
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkinIconButton(Icons.Filled.ArrowBack, "Retour", actions.onBack, size = 38, iconSize = 22)
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text(state.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                SubtitleLine(state)
            }
            TrackIcons(state, actions, Color.White, size = 38, iconSize = 20)
            if (state.locked) SkinIconButton(Icons.Filled.Lock, "Déverrouiller", actions.onToggleLock)
        }
    }

    @Composable
    override fun CenterControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        // YouTube : grand triangle plat, sans cercle ni fond.
        SkinPlayButton(
            playing = state.playing,
            onClick = actions.onTogglePlay,
            accent = RED,
            size = playSize,
            filled = false,
            iconColor = Color.White,
        )
    }

    @Composable
    override fun BottomControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Box(modifier.fillMaxWidth().background(Color.Black.copy(alpha = scrim))) {
            Column(Modifier.fillMaxWidth()) {
                // La barre rouge est collée au bas ; la rangée d'icônes vit au-dessus.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkinIconButton(
                        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Lecture/Pause", actions.onTogglePlay, size = 38,
                    )
                    SkinIconButton(
                        Icons.Filled.SkipNext, "Suivant",
                        actions.onNext, enabled = state.hasNext, size = 38, iconSize = 22,
                    )
                    Text(
                        "${fmtTime(state.positionMs)} / ${fmtTime(state.durationMs)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    SkinIconButton(Icons.Filled.Replay10, "Reculer de 10 s", { actions.onJumpRelative(-10) }, size = 36, iconSize = 20)
                    SkinIconButton(Icons.Filled.Forward10, "Avancer de 10 s", { actions.onJumpRelative(10) }, size = 36, iconSize = 20)
                    SkinChip("${state.speed}×", active = state.speed != 1f, accent = RED, onClick = actions.onCycleSpeed, border = false)
                    Spacer(Modifier.width(4.dp))
                    TrackIcons(state, actions, RED, size = 36, iconSize = 20)
                    SkinIconButton(
                        Icons.Filled.Speed, "Vitesse", actions.onCycleSpeed, size = 36, iconSize = 20,
                    )
                    AllToolsIconsOnly(state, actions, RED)
                }
                if (!state.progressOnTop) {
                    SkinSeekBar(
                        state, actions, RED,
                        modifier = Modifier.padding(horizontal = 0.dp),
                        thickness = 4,
                        rounded = false,
                        showTimes = false,
                        thumbSize = 10,
                    )
                }
            }
            MegaSkipCluster(
                state = state,
                actions = actions,
                accent = RED,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 74.dp),
            )
        }
    }

    /** Version compacte des outils (filtres, stats, rafraîchir, plus). */
    @Composable
    private fun AllToolsIconsOnly(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        accent: Color,
    ) {
        SkinIconButton(
            Icons.Filled.Tune, "Filtres vidéo", actions.onOpenFilters,
            tint = if (state.filterActive) accent else Color.White, size = 36, iconSize = 20,
        )
        SkinIconButton(
            Icons.Filled.Info, "Statistiques", actions.onToggleStats,
            tint = if (state.statsVisible) accent else Color.White, size = 36, iconSize = 20,
        )
        SkinIconButton(
            Icons.Filled.Refresh, "Rafraîchir les segments", actions.onRefreshSkip,
            tint = if (state.skipLoading) accent else Color.White, size = 36, iconSize = 20,
        )
        SkinIconButton(
            Icons.Filled.MoreVert, "Plus", actions.onOpenMore,
            size = 36, iconSize = 20,
        )
    }
}
