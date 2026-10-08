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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Bases communes, sans moteur de lecture ni gestes dupliqués. */
abstract class OriginalTheme : PlayerTheme {
    override val scrim = 0f
    override val accent = Color(0xFF00BCD4)
    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) =
        DefaultTheme.TopControls(state, actions, modifier)
    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(modifier.fillMaxWidth().padding(12.dp)) {
            SkipAndSettings(state, actions, accent)
            SkinSeekBar(state, actions, accent, showTimes = false)
            TimeAndActions(state, actions, accent)
        }
    }
}

object ZenTheme : OriginalTheme() {
    override val id = "zen"
    override val label = "Zen"
    override val accent = Color.White
    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier.fillMaxWidth().padding(12.dp)) {
            SkinIconButton(Icons.Default.ArrowBack, "Retour", actions.onBack, tint = Color.White.copy(alpha = 0.7f))
        }
    }
    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        // Les réglages restent accessibles en touchant les temps en bas.
        Box(modifier) {
            SkinIconButton(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Lecture ou pause", actions.onTogglePlay, size = 72, iconSize = 56)
        }
    }
    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SkinSeekBar(state, actions, accent, showTimes = false)
            Text("${fmtTime(state.positionMs)} / ${fmtTime(state.durationMs)}", color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.clickable(onClick = actions.onOpenMore).padding(8.dp))
        }
    }
}

object OrbitTheme : OriginalTheme() {
    override val id = "orbit"
    override val label = "Orbit"
    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Box(modifier.size(240.dp, 160.dp), contentAlignment = Alignment.Center) {
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, 72, filled = true)
            Box(Modifier.offset((-80).dp, (-36).dp)) { OrbitButton(Icons.Default.Replay10, "Reculer 10 secondes", { actions.onJumpRelative(-10) }) }
            Box(Modifier.offset(80.dp, (-36).dp)) { OrbitButton(Icons.Default.Forward10, "Avancer 10 secondes", { actions.onJumpRelative(10) }) }
            Box(Modifier.offset((-80).dp, 36.dp)) { OrbitButton(Icons.Default.PlaylistPlay, "Playlist", actions.onOpenPlaylist) }
            Box(Modifier.offset(80.dp, 36.dp)) { OrbitButton(Icons.Default.Settings, "Paramètres", actions.onOpenMore) }
        }
    }
    @Composable
    private fun OrbitButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, action: () -> Unit) {
        SkinIconButton(icon, label, action, size = 44, background = Color(0xDD1A1A1A), border = accent)
    }
}

object CompactBarTheme : OriginalTheme() {
    override val id = "compactbar"
    override val label = "Compact Bar"
    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Box(modifier) { SkinIconButton(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            "Lecture ou pause", actions.onTogglePlay, size = 64, iconSize = 48) }
    }
    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier.fillMaxWidth().padding(8.dp).background(Color(0xDD1A1A1A), RoundedCornerShape(24.dp)), verticalAlignment = Alignment.CenterVertically) {
            SkinIconButton(Icons.Default.Replay10, "Reculer", { actions.onJumpRelative(-10) }, size = 36)
            SkinIconButton(Icons.Default.Forward10, "Avancer", { actions.onJumpRelative(10) }, size = 36)
            Box(Modifier.weight(1f)) { SkinSeekBar(state, actions, accent, showTimes = false) }
            SkinIconButton(Icons.Default.PlaylistPlay, "Playlist", actions.onOpenPlaylist, size = 36)
            SkinIconButton(Icons.Default.Settings, "Paramètres", actions.onOpenMore, size = 36)
        }
    }
}

object NeonFrameTheme : OriginalTheme() {
    override val id = "neonframe"
    override val label = "Neon Frame"
    override val accent = Color(0xFF00E5FF)
    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier.border(1.dp, accent, RoundedCornerShape(40.dp)).padding(8.dp).background(Color(0xE61A1A1A)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SkinIconButton(Icons.Default.Replay10, "Reculer", { actions.onJumpRelative(-10) }, tint = accent)
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, 64, iconColor = accent)
            SkinIconButton(Icons.Default.Forward10, "Avancer", { actions.onJumpRelative(10) }, tint = Color(0xFFFF00E5))
        }
    }
    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        super.BottomControls(state, actions, modifier.padding(8.dp).border(1.dp, accent, RoundedCornerShape(16.dp)).background(Color(0xE61A1A1A)))
    }
}

object SplitControlsTheme : OriginalTheme() {
    override val id = "split"
    override val label = "Split Controls"
    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable { actions.onJumpRelative(-10) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Replay10, "Reculer 10 secondes", tint = Color.White.copy(alpha = 0.4f))
            }
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, 64)
            Box(Modifier.weight(1f).fillMaxHeight().clickable { actions.onJumpRelative(10) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Forward10, "Avancer 10 secondes", tint = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}

object FloatingCardsTheme : OriginalTheme() {
    override val id = "floating"
    override val label = "Floating Cards"
    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        DefaultTheme.TopControls(state, actions, modifier.padding(12.dp).background(Color(0xE61A1A1A), RoundedCornerShape(16.dp)))
    }
    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, 72, filled = true)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SkinIconButton(Icons.Default.Replay10, "Reculer", { actions.onJumpRelative(-10) }, background = Color(0xE61A1A1A))
                Spacer(Modifier.width(24.dp))
                SkinIconButton(Icons.Default.Forward10, "Avancer", { actions.onJumpRelative(10) }, background = Color(0xE61A1A1A))
            }
        }
    }
    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        super.BottomControls(state, actions, modifier.padding(12.dp).background(Color(0xE61A1A1A), RoundedCornerShape(16.dp)))
    }
}
