@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The new main player: quiet title rail, readable transport, one restrained action dock. */
object CinemaTheme : PlayerTheme {
    override val id = "cinema"
    override val label = "Essentiel"
    override val accent = Color(0xFF73B7FF)
    override val scrim = 0f

    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .82f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SkinIconButton(Icons.Default.ArrowBack, "Fermer le lecteur", actions.onBack, size = 48, iconSize = 24)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(state.title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.subtitle.isNotBlank()) Text(state.subtitle, color = Color.White.copy(alpha = .65f),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SkinIconButton(Icons.Default.LockOpen, "Verrouiller les commandes", actions.onToggleLock, size = 48)
            SkinIconButton(Icons.Default.Fullscreen, "Changer l'orientation", actions.onToggleOrientation, size = 48)
        }
    }

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 480.dp
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (wide) 28.dp else 20.dp)) {
                if (wide) SkinIconButton(Icons.Default.SkipPrevious, "Épisode précédent", actions.onPrev,
                    enabled = state.hasPrev, size = 48, iconSize = 28)
                Jump(-state.skipSeconds, actions)
                Surface(onClick = actions.onTogglePlay, color = Color.White,
                    shape = RoundedCornerShape(26.dp), modifier = Modifier.size(80.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (state.playing) "Mettre en pause" else "Lire", tint = Color(0xFF101722), modifier = Modifier.size(40.dp))
                    }
                }
                Jump(state.skipSeconds, actions)
                if (wide) SkinIconButton(Icons.Default.SkipNext, "Épisode suivant", actions.onNext,
                    enabled = state.hasNext, size = 48, iconSize = 28)
            }
        }
    }

    @Composable
    private fun Jump(seconds: Int, actions: PlayerControlsActions) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SkinIconButton(if (seconds < 0) Icons.Default.Replay else Icons.Default.FastForward,
                if (seconds < 0) "Reculer de ${-seconds} secondes" else "Avancer de $seconds secondes",
                { actions.onJumpRelative(seconds) }, size = 48, iconSize = 28)
            Text("${if (seconds > 0) "+" else "−"}${kotlin.math.abs(seconds)} s",
                color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }

    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f))))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 12.dp, vertical = 12.dp)) {
            Sections(state, actions, top = false)
        }
    }

    @Composable
    internal fun Sections(state: PlayerControlsState, actions: PlayerControlsActions, top: Boolean) {
        val placement = PlayerBarPlacement(state.progressOnTop, state.toolsOnTop)
        if (placement.showsProgress(top)) {
            SkinSeekBar(state, actions, accent, showTimes = false)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(state.dragFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs),
                    color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text(fmtTime(state.durationMs), color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (placement.showsTools(top)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                state.activeSkip?.let { Pill("Passer ${it.type.label}", actions.onSkipSegment, true) }
                Pill("Épisodes", actions.onOpenPlaylist)
                Pill("Audio", actions.onOpenAudio)
                Pill("Sous-titres", actions.onOpenSubtitles)
                if (state.hasLinks) Pill("Qualité", actions.onOpenQuality)
                Pill("${state.speed}×", actions.onCycleSpeed)
                Pill("+${state.megaSkipSeconds} s", actions.onMegaJump)
                state.customSkips.filter { it.enabled }.forEach { skip -> Pill(skip.label, { actions.onCustomSkip(skip) }) }
                Pill("Options", actions.onOpenMore)
            }
        }
    }

    @Composable
    private fun Pill(label: String, action: () -> Unit, active: Boolean = false) {
        Surface(onClick = action, shape = RoundedCornerShape(12.dp),
            color = if (active) accent else Color.White.copy(alpha = .10f),
            contentColor = if (active) Color(0xFF101722) else Color.White) {
            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
