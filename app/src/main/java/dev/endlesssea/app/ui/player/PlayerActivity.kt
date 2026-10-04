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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
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
    val subtitleTracks by viewModel.engine.availableSubtitles.collectAsState()
    val audioTracks by viewModel.engine.availableAudio.collectAsState()

    val context = LocalContext.current
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var showCcDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(1f) }
    // Cycle de zoom : ajuster (fit) → zoom plein écran → remplir
    val zoomModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
        )
    }
    var zoomIndex by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.engine.player
                    useController = false
                }.also { playerView = it }
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
                // Zoom plein écran (fit → zoom → fill)
                IconButton(onClick = {
                    zoomIndex = (zoomIndex + 1) % zoomModes.size
                    playerView?.resizeMode = zoomModes[zoomIndex]
                }) {
                    Icon(
                        Icons.Filled.ZoomIn,
                        "Zoom « plein écran »",
                        tint = if (zoomIndex == 0) Color.White else MaterialTheme.colorScheme.primary,
                    )
                }
                // Sous-titres
                if (subtitleTracks.isNotEmpty()) {
                    IconButton(onClick = { showCcDialog = true }) {
                        Icon(Icons.Filled.ClosedCaption, "Sous-titres", tint = Color.White)
                    }
                }
                // Piste audio
                if (audioTracks.size > 1) {
                    IconButton(onClick = { showAudioDialog = true }) {
                        Icon(Icons.Filled.Audiotrack, "Piste audio", tint = Color.White)
                    }
                }
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

                // ---- Luminosité locale (glissière en bas à gauche)
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.BrightnessMedium, "Luminosité", tint = Color.White)
                    Slider(
                        value = brightness,
                        onValueChange = { v ->
                            brightness = v
                            (context as? android.app.Activity)?.let { activity ->
                                val lp = activity.window.attributes
                                lp.screenBrightness = v.coerceIn(0f, 1f)
                                activity.window.attributes = lp
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp).fillMaxWidth(0.28f),
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

    // ---- Boîte « Sous-titres »
    if (showCcDialog) {
        AlertDialog(
            onDismissRequest = { showCcDialog = false },
            confirmButton = { TextButton(onClick = { showCcDialog = false }) { Text("Fermer") } },
            title = { Text("Sous-titres") },
            text = {
                Column {
                    Text(
                        "Désactivé",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.engine.selectSubtitle(null); showCcDialog = false }
                            .padding(vertical = 10.dp),
                    )
                    subtitleTracks.forEach { track ->
                        Text(
                            track.label.ifBlank { track.lang },
                            color = Color.Unspecified,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.engine.selectSubtitle(track); showCcDialog = false }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
        )
    }

    // ---- Boîte « Piste audio »
    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            confirmButton = { TextButton(onClick = { showAudioDialog = false }) { Text("Fermer") } },
            title = { Text("Piste audio") },
            text = {
                Column {
                    audioTracks.forEach { track ->
                        Text(
                            listOfNotNull(track.label.ifBlank { null }, track.language)
                                .joinToString(" · ").ifBlank { track.id },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.engine.selectAudio(track.id); showAudioDialog = false }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
        )
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
