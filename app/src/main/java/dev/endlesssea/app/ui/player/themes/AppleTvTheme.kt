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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Thème **Apple TV+** — minimalisme absolu : aucun voile, titre en haut au
 * centre (majuscules espacées), un unique bouton lecture cerclé au centre,
 * ligne de temps d'un pixel et une rangée discrète (sous-titres, audio,
 * vitesse, plus) tout en bas.
 */
object AppleTvTheme : PlayerTheme {
    override val id = "appletv"
    override val label = "Apple TV+"
    override val accent = Color.White
    override val scrim = 0.0f
    override val progressThickness = 2
    override val rounded = true
    override val playSize = 84

    @Composable
    override fun TopControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Row(
            modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkinIconButton(Icons.Filled.Close, "Fermer", actions.onBack, size = 38, iconSize = 22)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.title.uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (state.subtitle.isNotBlank()) {
                    Text(
                        state.subtitle,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.locked) {
                SkinIconButton(Icons.Filled.Lock, "Déverrouiller", actions.onToggleLock)
            } else {
                Spacer(Modifier.width(38.dp))
            }
        }
    }

    @Composable
    override fun CenterControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            SkinPlayButton(
                playing = state.playing,
                onClick = actions.onTogglePlay,
                accent = Color.White,
                size = playSize,
                filled = false,
                iconColor = Color.White,
            )
            Row(
                Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "− 10 s",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(6.dp),
                )
                Text(
                    "+ 10 s",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(6.dp),
                )
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
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackIcons(state, actions, Color.White, size = 38, iconSize = 20)
                    Spacer(Modifier.weight(1f))
                    SkinChip(
                        "${state.speed}×", active = false, accent = Color.White,
                        onClick = actions.onCycleSpeed, border = false,
                    )
                    SkinIconButton(
                        Icons.Filled.MoreVert, "Plus",
                        actions.onOpenMore, size = 38, iconSize = 20,
                    )
                }
                SkinSeekBar(
                    state, actions, Color.White,
                    modifier = Modifier.padding(horizontal = 0.dp),
                    thickness = 2, rounded = true, showTimes = false, thumbSize = 8,
                )
            }
            MegaSkipCluster(
                state = state,
                actions = actions,
                accent = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 72.dp),
            )
        }
    }
}
