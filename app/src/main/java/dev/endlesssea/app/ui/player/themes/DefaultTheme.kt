package dev.endlesssea.app.ui.player.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Thème **Endless Sea** — l'habillage d'origine : bandeau supérieur sombre,
 * transport centré, ligne de temps et outils en bas (ou en haut selon la
 * disposition réglée par l'utilisateur). Sert de repli aux autres thèmes.
 */
object DefaultTheme : PlayerTheme {
    override val id = "default"
    override val label = "Endless Sea (défaut)"
    override val scrim = 0.55f
    override val progressThickness = 4
    override val rounded = true
    override val playSize = 78

    @Composable
    override fun TopControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        val accent = accentColor()
        Column(modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkinIconButton(Icons.Filled.ArrowBack, "Retour", actions.onBack)
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    SubtitleLine(state)
                }
                TrackIcons(state, actions, Color.White)
                if (state.locked) {
                    SkinIconButton(Icons.Filled.Lock, "Déverrouiller", actions.onToggleLock)
                }
            }
            // §placements : ce qui a été réglé « en haut » vit sous le titre.
            if (state.toolsOnTop) AllTools(state, actions, accent, state.progressOnTop)
            if (state.progressOnTop) SkinSeekBar(state, actions, accent)
        }
    }

    @Composable
    override fun CenterControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkinSkipButton(next = false, enabled = state.hasPrev, onClick = actions.onPrev)
            SkinPlayButton(
                playing = state.playing,
                onClick = actions.onTogglePlay,
                accent = accentColor(),
                size = playSize,
            )
            SkinSkipButton(next = true, enabled = state.hasNext, onClick = actions.onNext)
        }
    }

    @Composable
    override fun BottomControls(
        state: PlayerControlsState,
        actions: PlayerControlsActions,
        modifier: Modifier,
    ) {
        val accent = accentColor()
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
                if (!state.progressOnTop) SkinSeekBar(state, actions, accent)
                if (!state.toolsOnTop) AllTools(state, actions, accent, state.progressOnTop)
            }
            MegaSkipCluster(
                state = state,
                actions = actions,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (state.progressOnTop) 24.dp else 104.dp),
            )
        }
    }
}
