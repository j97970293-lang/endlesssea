package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
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
 * Thème **Netflix** — rouge #E50914, barre de progression épaisse à angles
 * vifs collée au bas de l'écran, gros bouton lecture central, et barre
 * d'outils « à la Netflix » : sauts ±10 s, épisode suivant, sous-titres,
 * audio et vitesse, le tout au-dessus de la barre.
 */
object NetflixTheme : PlayerTheme {
    private val RED = Color(0xFFE50914)
    override val id = "netflix"
    override val label = "Netflix"
    override val accent = RED
    override val scrim = 0.78f
    override val progressThickness = 6
    override val rounded = false
    override val playSize = 92

    @Composable
    override fun TopControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Column(modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.62f))) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinIconButton(Icons.Filled.ArrowBack, "Retour", actions.onBack, size = 44, iconSize = 26)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        state.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    SubtitleLine(state)
                }
                TrackIcons(state, actions, Color.White, size = 44, iconSize = 24)
                SkinIconButton(Icons.Filled.MoreVert, "Plus", actions.onOpenMore, size = 44)
                if (state.locked) SkinIconButton(Icons.Filled.Lock, "Déverrouiller", actions.onToggleLock)
            }
            if (state.toolsOnTop || state.progressOnTop) {
                if (state.toolsOnTop && !state.locked) {
                    AllTools(state, actions, RED, state.progressOnTop)
                }
                if (state.progressOnTop) {
                    SkinSeekBar(state, actions, RED, thickness = 6, rounded = false, showTimes = true)
                }
            }
        }
    }

    @Composable
    override fun CenterControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        SkinPlayButton(
            playing = state.playing,
            onClick = actions.onTogglePlay,
            accent = RED,
            size = playSize,
            filled = true,
            square = false,
        )
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
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    )
                    .padding(top = 26.dp),
            ) {
                // Barre d'outils « Netflix » : lecture, ±10 s, suivant, pistes, vitesse.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SkinIconButton(
                        if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Lecture/Pause",
                        actions.onTogglePlay,
                        size = 46, iconSize = 30,
                    )
                    SkinIconButton(Icons.Filled.Replay10, "Reculer de 10 s", { actions.onJumpRelative(-10) }, size = 44)
                    SkinIconButton(Icons.Filled.Forward10, "Avancer de 10 s", { actions.onJumpRelative(10) }, size = 44)
                    SkinIconButton(
                        Icons.Filled.SkipNext, "Épisode suivant", actions.onNext,
                        enabled = state.hasNext, size = 44,
                    )
                    if (state.speed != 1f) {
                        SkinChip("${state.speed}×", active = true, accent = RED, onClick = actions.onCycleSpeed)
                    }
                    Spacer(Modifier.weight(1f))
                    SkinIconButton(Icons.Filled.Speed, "Vitesse", actions.onCycleSpeed, size = 40)
                    TrackIcons(state, actions, Color.White, size = 40, iconSize = 22)
                }
                if (!state.progressOnTop) {
                    SkinSeekBar(
                        state, actions, RED,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        thickness = 6,
                        rounded = false,
                        showTimes = false,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${fmtTime(state.positionMs)} · ${fmtTime(state.durationMs)}",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        state.activeSkip?.let { seg ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                                    .clickable(onClick = actions.onSkipSegment)
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    if (seg.type.name.contains("INTRO")) "Passer l'intro ▶"
                                    else "Passer ▶",
                                    color = Color.Black,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                    }
                }
            }
            if (state.locked) {
                // Verrouillé : seule la ligne de temps reste visible.
                SkinSeekBar(
                    state, actions, RED,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    thickness = 6, rounded = false, showTimes = false,
                )
            }
            MegaSkipCluster(
                state = state,
                actions = actions,
                accent = RED,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (state.progressOnTop) 26.dp else 112.dp),
            )
        }
    }
}
