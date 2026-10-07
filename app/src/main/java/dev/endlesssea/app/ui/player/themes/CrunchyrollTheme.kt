package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Thème **Crunchyroll** — orange #F47521 : barre de progression arrondie et
 * épaisse, boutons circulaires entourés autour du bouton lecture, outils bas
 * colorés à l'orange. Les pastilles « Passer le générique » sont mises en
 * avant (orange plein).
 */
object CrunchyrollTheme : PlayerTheme {
    private val ORANGE = Color(0xFFF47521)
    override val id = "crunchyroll"
    override val label = "Crunchyroll"
    override val accent = ORANGE
    override val scrim = 0.60f
    override val progressThickness = 8
    override val rounded = true
    override val playSize = 84

    @Composable
    override fun TopControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.42f))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinIconButton(Icons.Filled.ArrowBack, "Retour", actions.onBack)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        color = ORANGE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    SubtitleLine(state)
                }
                TrackIcons(state, actions, ORANGE)
                if (state.locked) SkinIconButton(Icons.Filled.Lock, "Déverrouiller", actions.onToggleLock)
            }
            if (state.toolsOnTop) AllTools(state, actions, ORANGE, state.progressOnTop)
            if (state.progressOnTop) {
                SkinSeekBar(state, actions, ORANGE, thickness = 8, rounded = true)
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
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(6.dp),
            ) {
                SkinIconButton(Icons.Filled.Replay10, "Reculer de 10 s", { actions.onJumpRelative(-10) }, size = 48, iconSize = 30)
            }
            Box(Modifier.padding(horizontal = 22.dp)) {
                SkinPlayButton(
                    playing = state.playing,
                    onClick = actions.onTogglePlay,
                    accent = ORANGE,
                    size = playSize,
                    filled = true,
                )
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(6.dp),
            ) {
                SkinIconButton(Icons.Filled.Forward10, "Avancer de 10 s", { actions.onJumpRelative(10) }, size = 48, iconSize = 30)
            }
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
                    SkinSeekBar(state, actions, ORANGE, thickness = 8, rounded = true)
                }
                if (!state.toolsOnTop) AllTools(state, actions, ORANGE, state.progressOnTop)
            }
            MegaSkipCluster(
                state = state,
                actions = actions,
                accent = ORANGE,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (state.progressOnTop) 24.dp else 108.dp),
            )
        }
    }
}
