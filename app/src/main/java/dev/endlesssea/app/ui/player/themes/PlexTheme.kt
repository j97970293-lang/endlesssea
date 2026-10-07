package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Thème **Plex** — ambre #E5A00D : voile sombre franc, gros bouton lecture
 * cerclé, sauts ±30 s de part et d'autre, ligne de temps avec **temps
 * restant** à droite (et non la durée), verrou et outils sur une seule rangée.
 */
object PlexTheme : PlayerTheme {
    private val AMBER = Color(0xFFE5A00D)
    override val id = "plex"
    override val label = "Plex"
    override val accent = AMBER
    override val scrim = 0.70f
    override val progressThickness = 5
    override val rounded = true
    override val playSize = 88

    @Composable
    override fun TopControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinIconButton(Icons.Filled.ArrowBack, "Retour", actions.onBack)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        color = AMBER,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    SubtitleLine(state)
                }
                TrackIcons(state, actions, AMBER)
                if (state.locked) SkinIconButton(Icons.Filled.Lock, "Déverrouiller", actions.onToggleLock)
            }
            if (state.toolsOnTop) AllTools(state, actions, AMBER, state.progressOnTop, compact = true)
            if (state.progressOnTop) {
                SkinSeekBar(state, actions, AMBER, thickness = 5, rounded = true, remaining = true)
            }
        }
    }

    @Composable
    override fun CenterControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            SkinIconButton(Icons.Filled.Replay30, "Reculer de 30 s", { actions.onJumpRelative(-30) }, size = 52, iconSize = 32)
            Box(Modifier.padding(horizontal = 24.dp)) {
                SkinPlayButton(
                    playing = state.playing,
                    onClick = actions.onTogglePlay,
                    accent = AMBER,
                    size = playSize,
                    filled = false,
                )
            }
            SkinIconButton(Icons.Filled.Forward30, "Avancer de 30 s", { actions.onJumpRelative(30) }, size = 52, iconSize = 32)
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
                if (!state.progressOnTop) {
                    SkinSeekBar(state, actions, AMBER, thickness = 5, rounded = true, remaining = true)
                }
                if (!state.toolsOnTop) {
                    AllTools(state, actions, AMBER, state.progressOnTop, compact = true)
                }
            }
            MegaSkipCluster(
                state = state,
                actions = actions,
                accent = AMBER,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (state.progressOnTop) 24.dp else 108.dp),
            )
        }
    }
}
