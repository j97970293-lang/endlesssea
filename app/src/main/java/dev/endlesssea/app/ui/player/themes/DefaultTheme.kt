package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Interface par défaut : titre seul en haut, outils juste au-dessus de la progression. */
object DefaultTheme : PlayerTheme {
    override val id = "default"
    override val label = "Défaut"
    override val accent = Color(0xFF00BCD4)
    override val scrim = 0f
    override val playSize = 64

    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkinIconButton(Icons.Default.ArrowBack, "Retour", actions.onBack)
            Text(state.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
    }

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            SkinIconButton(Icons.Default.Replay10, "Reculer de 10 secondes", { actions.onJumpRelative(-10) }, size = 44, iconSize = 28)
            SkinPlayButton(state.playing, actions.onTogglePlay, accent, playSize)
            SkinIconButton(Icons.Default.Forward10, "Avancer de 10 secondes", { actions.onJumpRelative(10) }, size = 44, iconSize = 28)
        }
    }

    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            SkipAndSettings(state, actions, accent)
            SkinSeekBar(state, actions, accent, showTimes = false)
            TimeAndActions(state, actions, accent)
        }
    }
}

@Composable
internal fun SkipAndSettings(state: PlayerControlsState, actions: PlayerControlsActions, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.activeSkip?.let {
                SkinChip("Passer ${it.type.label}" + (state.skipCountdown?.let { n -> " ($n)" } ?: ""), false, accent, actions.onSkipSegment, border = false)
            }
            state.customSkips.filter { it.enabled }.forEach { button ->
                SkinChip(button.label, false, accent, { actions.onCustomSkip(button) }, border = false)
            }
        }
        // §megaskip-right : déplacer le bouton megaskip à droite
        SkinChip("+${state.megaSkipSeconds}s", false, accent, actions.onMegaJump, border = false)
        SkinIconButton(Icons.Default.Settings, "Paramètres du lecteur", actions.onOpenMore)
        SkinIconButton(Icons.Default.Info, "Informations techniques", actions.onToggleStats)
    }
}

@Composable
internal fun TimeAndActions(state: PlayerControlsState, actions: PlayerControlsActions, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(fmtTime(state.dragFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs), color = Color.White)
        Spacer(Modifier.weight(1f))
        SkinChip("${state.speed}x", false, accent, actions.onCycleSpeed, border = false)
        SkinIconButton(Icons.Default.ClosedCaption, "Sous-titres", actions.onOpenSubtitles)
        SkinIconButton(Icons.Default.PlaylistPlay, "Playlist", actions.onOpenPlaylist)
        SkinIconButton(Icons.Default.Fullscreen, "Orientation plein écran", actions.onToggleOrientation)
        Spacer(Modifier.weight(1f))
        Text(fmtTime(state.durationMs), color = Color.White)
    }
}
