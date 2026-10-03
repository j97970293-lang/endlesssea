package dev.endlesssea.app.ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.viewModels
import androidx.media3.ui.PlayerView
import dagger.hilt.android.AndroidEntryPoint
import dev.endlesssea.app.ui.theme.EndlessSeaTheme

/**
 * Lecteur immersif (spec §7) : SurfaceView ExoPlayer + surcouche Compose
 * (position, titre, vitesse, verrou, ±10 s, lecture/pause, verrouillage des gestes).
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val launch = PlayerLaunchStore.consume()
        if (launch.links.isNotEmpty()) {
            viewModel.prepare(
                mediaId = launch.mediaId,
                episodeId = launch.episodeId,
                title = launch.title,
                links = launch.links,
                startIndex = launch.startIndex,
            )
        }

        setContent {
            EndlessSeaTheme { PlayerScreen(viewModel, onBack = { finish() }) }
        }
    }

    override fun onPause() {
        // La position est déjà persistée toutes les 5 s par la boucle du ViewModel
        // (spec §7 « mémorisation ») ; on fige la lecture.
        super.onPause()
        runCatching { viewModel.engine.pause() }
    }

    override fun onDestroy() {
        runCatching { viewModel.engine.release() }
        super.onDestroy()
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val isPlaying by viewModel.engine.isPlaying.collectAsState()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.engine.player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Tap : afficher/masquer les contrôles (sauf si verrouillé)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(enabled = !state.locked) { viewModel.toggleControls() },
        )

        if (state.controlsVisible || state.locked) {
            // ---- Barre supérieure
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour", tint = Color.White) }
                Text(
                    state.title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                IconButton(onClick = { viewModel.toggleLock() }) {
                    Icon(
                        if (state.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        if (state.locked) "Verrouillé" else "Verrouiller",
                        tint = Color.White,
                    )
                }
            }

            if (!state.locked) {
                // ---- Contrôles centraux
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "−10 s",
                        color = Color.White,
                        modifier = Modifier.clickable { viewModel.engine.seekBy(-10_000) }.padding(16.dp),
                    )
                    IconButton(onClick = { if (isPlaying) viewModel.engine.pause() else viewModel.engine.play() }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Lecture/Pause", tint = Color.White,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                    Text(
                        "+10 s",
                        color = Color.White,
                        modifier = Modifier.clickable { viewModel.engine.seekBy(10_000) }.padding(16.dp),
                    )
                }

                // ---- Vitesse
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                ) {
                    listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        Text(
                            "${speed}×",
                            color = if (state.speed == speed) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { viewModel.setSpeed(speed) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error?.let { error ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = Color.White)
                    TextButtonBack(onBack)
                }
            }
        }
    }
}

@Composable
private fun TextButtonBack(onBack: () -> Unit) {
    Text(
        "Retour",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onBack).padding(12.dp),
    )
}
