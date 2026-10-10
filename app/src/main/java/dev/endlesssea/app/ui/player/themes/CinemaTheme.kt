@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Reference layout: header / previous-play-next / megaskip / timeline / time-and-tools dock. */
object CinemaTheme : PlayerTheme {
    override val id = "cinema"
    override val label = "Essentiel"
    override val accent = Color(0xFF68D0F3)
    override val scrim = 0f
    private val panel = Color(0xE611131A)
    private val outline = Color.White.copy(alpha = .12f)

    @Composable
    override fun TopControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .85f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Control(Icons.Default.ArrowBack, "Fermer le lecteur", actions.onBack, tint = accent)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(state.title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.subtitle.isNotBlank()) Surface(color = accent.copy(alpha = .12f), shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 4.dp)) {
                    Text(state.subtitle, color = accent, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            Control(Icons.Default.Lock, "Verrouiller les commandes", actions.onToggleLock)
            Spacer(Modifier.width(6.dp))
            Surface(onClick = actions.onCycleZoom, color = panel, shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, outline)) {
                Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AspectRatio, "Changer le cadrage vidéo", tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(zoomLabel(state.zoomMode, state.manualZoom), color = Color.White, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 5.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Control(Icons.Default.Settings, "Paramètres du lecteur", actions.onOpenMore)
        }
    }

    @Composable
    override fun CenterControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Control(Icons.Default.SkipPrevious, "Épisode précédent", actions.onPrev, enabled = state.hasPrev, size = 56)
            // Keep the play button explicitly approved by the user.
            Surface(onClick = actions.onTogglePlay, color = Color.White,
                shape = RoundedCornerShape(26.dp), modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.playing) "Mettre en pause" else "Lire", tint = Color(0xFF101722), modifier = Modifier.size(40.dp))
                }
            }
            Control(Icons.Default.SkipNext, "Épisode suivant", actions.onNext, enabled = state.hasNext, size = 56)
        }
    }

    @Composable
    override fun BottomControls(state: PlayerControlsState, actions: PlayerControlsActions, modifier: Modifier) {
        Column(modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .94f))))
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 10.dp)) {
            Sections(state, actions, top = false)
        }
    }

    @Composable
    internal fun Sections(state: PlayerControlsState, actions: PlayerControlsActions, top: Boolean) {
        // This layout deliberately keeps the timeline and tools at the bottom, like the reference.
        // Legacy top/tools preferences remain available to the legacy themes only.
        if (!ReferencePlayerPlacement.showBottomSections(top)) return
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.megaSkipLeft) MegaSkip(state, actions)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.activeSkip?.let { skip ->
                    TextButton(onClick = actions.onSkipSegment) {
                        Text("Passer ${skip.type.label}", color = accent)
                    }
                }
                state.customSkips.filter { it.enabled }.forEach { skip ->
                    TextButton(onClick = { actions.onCustomSkip(skip) }) { Text(skip.label, color = Color.White) }
                }
            }
            if (!state.megaSkipLeft) MegaSkip(state, actions)
        }
        SeekBar(state, actions)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimePill(fmtTime(state.dragFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs))
            Surface(color = panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, outline), modifier = Modifier.weight(1f)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Control(Icons.Default.PlaylistPlay, "Liste des épisodes", actions.onOpenPlaylist)
                    Control(Icons.Default.Tune, "Filtres vidéo", actions.onOpenFilters)
                    Control(Icons.Default.HighQuality, "Qualité vidéo", actions.onOpenQuality, enabled = state.hasLinks)
                    Control(Icons.Default.Subtitles, "Sous-titres", actions.onOpenSubtitles)
                    Control(Icons.Default.VolumeUp, "Piste audio", actions.onOpenAudio)
                    Control(Icons.Default.Speed, "Vitesse ${state.speed}×", actions.onCycleSpeed)
                    Control(Icons.Default.ScreenRotation, "Changer l'orientation", actions.onToggleOrientation)
                    Control(Icons.Default.Replay, "Reculer de ${state.skipSeconds} secondes", { actions.onJumpRelative(-state.skipSeconds) })
                    Control(Icons.Default.FastForward, "Avancer de ${state.skipSeconds} secondes", { actions.onJumpRelative(state.skipSeconds) })
                }
            }
            TimePill(fmtTime(state.durationMs))
        }
    }

    @Composable
    private fun MegaSkip(state: PlayerControlsState, actions: PlayerControlsActions) {
        Surface(onClick = actions.onMegaJump, color = panel, shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .45f))) {
            Box(Modifier.heightIn(min = 48.dp).widthIn(min = 80.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text("+${state.megaSkipSeconds}", color = Color.White, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    @Composable
    private fun Control(icon: ImageVector, description: String, action: () -> Unit,
                        enabled: Boolean = true, tint: Color = Color.White, size: Int = 48) {
        Surface(onClick = action, enabled = enabled, shape = RoundedCornerShape(16.dp), color = panel,
            border = BorderStroke(1.dp, outline), modifier = Modifier.size(size.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = .3f), modifier = Modifier.size(24.dp))
            }
        }
    }

    @Composable
    private fun TimePill(text: String) {
        Surface(color = panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, outline)) {
            Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }

    @Composable
    private fun SeekBar(state: PlayerControlsState, actions: PlayerControlsActions) {
        val duration = state.durationMs.coerceAtLeast(1L)
        val position = (state.dragFraction ?: (state.positionMs.toFloat() / duration)).coerceIn(0f, 1f)
        val trackShape = RoundedCornerShape(if (state.progressRounded) 20.dp else 0.dp)
        Slider(value = position, onValueChange = actions.onDrag,
            onValueChangeFinished = { actions.onDragFinished(position) }, enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
            thumb = { Box(Modifier.width(if (state.autoHideThumb && state.dragFraction == null) 0.dp else 4.dp)
                .height(state.thumbSize.dp).background(accent, RoundedCornerShape(4.dp))) },
            track = {
                Box(Modifier.fillMaxWidth().height(maxOf(state.progressThickness, state.bufferThickness, 1).dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(state.progressThickness.coerceAtLeast(1).dp).background(accent.copy(alpha = .18f), trackShape))
                    Box(Modifier.fillMaxWidth((state.bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f))
                        .height(state.bufferThickness.coerceAtLeast(1).dp).background(Color.White.copy(alpha = .18f), trackShape))
                    Box(Modifier.fillMaxWidth(position).height(state.progressThickness.coerceAtLeast(1).dp).background(accent, trackShape))
                }
            })
    }
}
