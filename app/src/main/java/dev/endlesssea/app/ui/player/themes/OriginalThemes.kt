package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * §ui-lecteur-v2 — les 6 habillages alternatifs, relookés avec la même
 * grammaire visuelle : verre fumé translucide (plus de blocs 0xCC opaques),
 * filets fins 1dp, coins doux, chips pilulaires.
 */
abstract class OriginalTheme : PlayerTheme {
    override val scrim = 0f
    override val accent = Color(0xFF00BCD4)

    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) =
        DefaultTheme.TopControls(state, actions, modifier)

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
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                PlayerBarContent(state, actions, accent, top = false)
            }
        }
    }
}

/** Zen : rien qui claque — blanc, fin, silencieux. */
object ZenTheme : OriginalTheme() {
    override val id = "zen"
    override val label = "Zen"
    override val accent = Color.White

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Box(modifier) {
            SkinIconButton(
                if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Lecture ou pause", actions.onTogglePlay,
                size = 72, iconSize = 52,
            )
        }
    }

    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerBarContent(state, actions, accent, top = false)
        }
    }
}

/** Orbit : cockpit radial — verres ronds reliés par un filet discret. */
object OrbitTheme : OriginalTheme() {
    override val id = "orbit"
    override val label = "Orbit"

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Box(modifier.size(240.dp, 160.dp), contentAlignment = Alignment.Center) {
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, 68, filled = true)
            Box(Modifier.offset((-78).dp, (-34).dp)) {
                OrbitButton(Icons.Default.Replay10, "Reculer 10 secondes", { actions.onJumpRelative(-10) })
            }
            Box(Modifier.offset(78.dp, (-34).dp)) {
                OrbitButton(Icons.Default.Forward10, "Avancer 10 secondes", { actions.onJumpRelative(10) })
            }
            Box(Modifier.offset((-78).dp, 34.dp)) {
                OrbitButton(Icons.Default.PlaylistPlay, "Playlist", actions.onOpenPlaylist)
            }
            Box(Modifier.offset(78.dp, 34.dp)) {
                OrbitButton(Icons.Default.Settings, "Paramètres", actions.onOpenMore)
            }
        }
    }

    @Composable
    private fun OrbitButton(icon: ImageVector, label: String, action: () -> Unit) {
        SkinIconButton(
            icon, label, action, size = 46,
            background = Color(0x66000000), border = Color.White.copy(alpha = 0.30f),
        )
    }
}

/** Compact Bar : un « dock » unique en pilule de verre au bord bas. */
object CompactBarTheme : OriginalTheme() {
    override val id = "compactbar"
    override val label = "Compact Bar"

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Box(modifier) {
            SkinIconButton(
                if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Lecture ou pause", actions.onTogglePlay, size = 64, iconSize = 46,
            )
        }
    }

    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .background(Color(0x8C000000), RoundedCornerShape(26.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            PlayerBarContent(state, actions, accent, top = false)
        }
    }
}

/** Neon Frame : même concept, exécution propre — filet néon 1dp sur verre noir. */
object NeonFrameTheme : OriginalTheme() {
    override val id = "neonframe"
    override val label = "Neon Frame"
    override val accent = Color(0xFF00E5FF)

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(
            modifier
                .border(1.dp, accent, RoundedCornerShape(40.dp))
                .background(Color(0x66000000), RoundedCornerShape(40.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SkinIconButton(Icons.Default.Replay10, "Reculer", { actions.onJumpRelative(-10) }, tint = accent)
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, 62, iconColor = accent)
            SkinIconButton(Icons.Default.Forward10, "Avancer", { actions.onJumpRelative(10) }, tint = Color(0xFFFF40E0))
        }
    }

    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .border(1.dp, accent, RoundedCornerShape(20.dp))
                .background(Color(0x8C000000), RoundedCornerShape(20.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            PlayerBarContent(state, actions, accent, top = false)
        }
    }
}

/** Split Controls : deux zones immenses de tap, icônes fantômes au centre. */
object SplitControlsTheme : OriginalTheme() {
    override val id = "split"
    override val label = "Split Controls"

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable { actions.onJumpRelative(-10) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Replay10, "Reculer 10 secondes",
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(40.dp),
                )
            }
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, 64)
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable { actions.onJumpRelative(10) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Forward10, "Avancer 10 secondes",
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

/** Floating Cards : cartes flottantes en verre, corners 20dp, filets fins. */
object FloatingCardsTheme : OriginalTheme() {
    override val id = "floating"
    override val label = "Floating Cards"

    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        DefaultTheme.TopControls(
            state, actions,
            modifier
                .padding(10.dp)
                .background(Color(0x8C000000), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp)),
        )
    }

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, 72, filled = true)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SkinIconButton(
                    Icons.Default.Replay10, "Reculer", { actions.onJumpRelative(-10) },
                    background = Color(0x8C000000), border = Color.White.copy(alpha = 0.22f),
                )
                SkinIconButton(
                    Icons.Default.Forward10, "Avancer", { actions.onJumpRelative(10) },
                    background = Color(0x8C000000), border = Color.White.copy(alpha = 0.22f),
                )
            }
        }
    }

    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .background(Color(0x8C000000), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            PlayerBarContent(state, actions, accent, top = false)
        }
    }
}
