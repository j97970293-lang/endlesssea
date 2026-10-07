package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Thème **Gaming** — HUD néon cyan #00E5FF : titre en majuscules, puces
 * d'état (vitesse, cadrage, statistiques, segments) en haut, gros boutons
 * encadrés au centre, barre de progression vive et outils à contours carrés.
 */
object GamingTheme : PlayerTheme {
    private val NEON = Color(0xFF00E5FF)
    override val id = "gaming"
    override val label = "Gaming"
    override val accent = NEON
    override val scrim = 0.85f
    override val progressThickness = 6
    override val rounded = false
    override val playSize = 96

    @Composable
    override fun TopControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.72f))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinIconButton(Icons.Filled.ArrowBack, "Retour", actions.onBack, tint = NEON)
                Text(
                    state.title.uppercase(),
                    color = NEON,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                TrackIcons(state, actions, NEON)
                if (state.locked) SkinIconButton(Icons.Filled.Lock, "Déverrouiller", actions.onToggleLock, tint = NEON)
            }
            // Puces d'état façon HUD.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinChip("×${state.speed}", active = state.speed != 1f, accent = NEON, onClick = actions.onCycleSpeed)
                SkinChip(zoomLabel(state.zoomMode), active = state.zoomMode != 0, accent = NEON, onClick = actions.onCycleZoom)
                SkinChip(
                    "STATS", active = state.statsVisible, accent = NEON,
                    onClick = actions.onToggleStats,
                )
                SkinChip(
                    "SEG ${state.customSkips.size}", active = false, accent = NEON,
                    onClick = actions.onOpenSkipEditor,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.activeSkip?.let { "1" } ?: "0"} ACTIF",
                    color = NEON.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (state.toolsOnTop) AllTools(state, actions, NEON, state.progressOnTop, compact = true, bordered = true)
            if (state.progressOnTop) SkinSeekBar(state, actions, NEON, thickness = 6, rounded = false)
        }
    }

    @Composable
    override fun CenterControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            SkinIconButton(
                Icons.Filled.Replay10, "Reculer de 10 s", { actions.onJumpRelative(-10) },
                tint = NEON, size = 56, iconSize = 34,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                border = NEON.copy(alpha = 0.7f),
            )
            Box(Modifier.padding(horizontal = 26.dp)) {
                SkinPlayButton(
                    playing = state.playing,
                    onClick = actions.onTogglePlay,
                    accent = NEON,
                    size = playSize,
                    filled = true,
                    square = true,
                )
            }
            SkinIconButton(
                Icons.Filled.Forward10, "Avancer de 10 s", { actions.onJumpRelative(10) },
                tint = NEON, size = 56, iconSize = 34,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                border = NEON.copy(alpha = 0.7f),
            )
        }
    }

    @Composable
    override fun BottomControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Box(modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = scrim)),
                        ),
                    )
                    .padding(vertical = 6.dp),
            ) {
                if (!state.progressOnTop) SkinSeekBar(state, actions, NEON, thickness = 6, rounded = false)
                if (!state.toolsOnTop) {
                    AllTools(state, actions, NEON, state.progressOnTop, compact = true, bordered = true)
                }
            }
            MegaSkipCluster(
                state = state,
                actions = actions,
                accent = NEON,
                pillTextColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (state.progressOnTop) 24.dp else 112.dp),
            )
        }
    }
}
