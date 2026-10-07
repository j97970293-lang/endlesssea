package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Thème **VLC** — sobre et fonctionnel : cône orange, barre fine à angles
 * vifs, boutons carrés alignés en bas à gauche (lecture, précédent, suivant)
 * et outils regroupés en bas à droite. Peu de voile pour laisser voir l'image.
 */
object VlcTheme : PlayerTheme {
    private val ORANGE = Color(0xFFFF8800)
    override val id = "vlc"
    override val label = "VLC"
    override val accent = ORANGE
    override val scrim = 0.30f
    override val progressThickness = 3
    override val rounded = false
    override val playSize = 64

    @Composable
    override fun TopControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Row(
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.30f))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkinIconButton(Icons.Filled.ArrowBack, "Retour", actions.onBack, size = 36, iconSize = 20)
            Text(
                state.title,
                color = ORANGE,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            SubtitleLine(state)
            if (state.locked) SkinIconButton(Icons.Filled.Lock, "Déverrouiller", actions.onToggleLock)
        }
    }

    @Composable
    override fun CenterControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        // VLC n'encombre pas le centre : petit cône translucide.
        Box(
            Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Black.copy(alpha = 0.28f)),
        ) {
            SkinPlayButton(
                playing = state.playing,
                onClick = actions.onTogglePlay,
                accent = ORANGE,
                size = playSize,
                filled = false,
                square = true,
                iconColor = ORANGE,
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
                    ),
            ) {
                SkinSeekBar(
                    state, actions, ORANGE,
                    thickness = 3, rounded = false,
                    timeColor = Color.White,
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SkinSkipButton(next = false, enabled = state.hasPrev, onClick = actions.onPrev, tint = ORANGE, size = 44, iconSize = 30)
                    SkinIconButton(
                        if (state.playing) Icons.Filled.Pause
                        else Icons.Filled.PlayArrow,
                        "Lecture/Pause", actions.onTogglePlay, tint = ORANGE, size = 44, iconSize = 30,
                        shape = RoundedCornerShape(2.dp),
                    )
                    SkinSkipButton(next = true, enabled = state.hasNext, onClick = actions.onNext, tint = ORANGE, size = 44, iconSize = 30)
                    Spacer(Modifier.weight(1f))
                    SkinChip(
                        zoomLabel(state.zoomMode), active = state.zoomMode != 0, accent = ORANGE,
                        onClick = actions.onCycleZoom, border = true,
                    )
                    SkinIconButton(Icons.Filled.Speed, "Vitesse", actions.onCycleSpeed, size = 40)
                    SkinIconButton(Icons.Filled.ScreenRotation, "Orientation", actions.onToggleOrientation, size = 40)
                    TrackIcons(state, actions, ORANGE, size = 40, iconSize = 22)
                    SkinIconButton(Icons.Filled.MoreVert, "Plus", actions.onOpenMore, size = 40)
                }
            }
            MegaSkipCluster(
                state = state,
                actions = actions,
                accent = ORANGE,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 104.dp),
            )
        }
    }
}
