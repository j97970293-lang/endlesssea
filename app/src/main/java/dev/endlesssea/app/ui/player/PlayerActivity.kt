package dev.endlesssea.app.ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dagger.hilt.android.AndroidEntryPoint
import dev.endlesssea.app.ui.theme.EndlessSeaTheme

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val launch = PlayerLaunchStore.consume()
        viewModel.prepare(
            mediaId = launch.mediaId,
            episodeId = launch.episodeId,
            title = launch.title.ifBlank { "Lecture" },
            links = launch.links,
            startIndex = launch.startIndex,
        )
        setContent {
            EndlessSeaTheme {
                PlayerScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.engine.pause()
    }
}

private val SPEED_STEPS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

private val BUILTIN_PRESETS = listOf(
    VideoFilterPreset("Aucun", 0f, 0f, 0f),
    VideoFilterPreset("Qualité+", 8f, 12f, 0f),
    VideoFilterPreset("Cinéma", -4f, -8f, 4f),
    VideoFilterPreset("Nuit douce", -12f, 0f, 0f),
)

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
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
    var showFilterDialog by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(1f) }
    var slidingPos by remember { mutableStateOf<Float?>(null) }

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
                    // TextureView : requis pour voir les filtres vidéo (HslAdjustment)
                    videoTextureView = android.view.TextureView(ctx)
                    player = viewModel.engine.player
                    useController = false
                }.also { playerView = it }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Gestuel : tap = contrôles, double tap ±skip (gauche / droite)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state.skipSeconds, state.locked) {
                    detectTapGestures(
                        onTap = { if (!state.locked) viewModel.toggleControls() },
                        onDoubleTap = { offset ->
                            if (!state.locked) {
                                val half = size.width / 2
                                viewModel.jumpBy(if (offset.x < half) -state.skipSeconds else state.skipSeconds)
                            }
                        },
                    )
                },
        )

        // Retour visuel du saut (double appui)
        state.skipFlash?.let { flash ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                ) {
                    Text(
                        flash,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }

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
                IconButton(onClick = {
                    zoomIndex = (zoomIndex + 1) % zoomModes.size
                    playerView?.resizeMode = zoomModes[zoomIndex]
                }) {
                    Icon(
                        Icons.Filled.ZoomIn, "Zoom plein écran",
                        tint = if (zoomIndex == 0) Color.White else MaterialTheme.colorScheme.primary,
                    )
                }
                if (subtitleTracks.isNotEmpty()) {
                    IconButton(onClick = { showCcDialog = true }) {
                        Icon(Icons.Filled.ClosedCaption, "Sous-titres", tint = Color.White)
                    }
                }
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
                // ---- Bas : glissière de temps + contrôles modernes
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                ) {
                    // Barre de progression + temps
                    val dur = state.durationMs.coerceAtLeast(1)
                    Slider(
                        value = (slidingPos ?: (state.positionMs.toFloat() / dur)).coerceIn(0f, 1f),
                        onValueChange = { slidingPos = it },
                        onValueChangeFinished = {
                            slidingPos?.let { viewModel.engine.player.seekTo((it * dur).toLong()) }
                            slidingPos = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            formatTime(slidingPos?.let { (it * dur).toLong() } ?: state.positionMs),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatTime(dur),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.BrightnessMedium, "Luminosité",
                            tint = Color.White, modifier = Modifier.padding(end = 8.dp),
                        )
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
                            modifier = Modifier.width(120.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.jumpBy(-state.skipSeconds) }) {
                            Text("−${state.skipSeconds}s", color = Color.White,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(
                            onClick = { if (isPlaying) viewModel.engine.pause() else viewModel.engine.play() },
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "Lecture/Pause", tint = Color.White,
                            )
                        }
                        IconButton(onClick = { viewModel.jumpBy(state.skipSeconds) }) {
                            Text("+${state.skipSeconds}s", color = Color.White,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.weight(1f))
                        // Vitesse (cycle)
                        Text(
                            "${state.speed}×",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clickable {
                                    val next = SPEED_STEPS[(SPEED_STEPS.indexOf(state.speed) + 1) % SPEED_STEPS.size]
                                    viewModel.setSpeed(next)
                                }
                                .padding(8.dp),
                        )
                        // Filtres vidéo (préréglages + préréglages sauvegardés)
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(
                                Icons.Filled.Tune, "Filtres vidéo",
                                tint = if (state.filterPresetName != "none" && state.filterPresetName != "Aucun")
                                    MaterialTheme.colorScheme.primary else Color.White,
                            )
                        }
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

    // ---- Boîte « Filtres vidéo » : préréglages + réglage fin + sauvegarde
    if (showFilterDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            confirmButton = { TextButton(onClick = { showFilterDialog = false }) { Text("Fermer") } },
            title = { Text("Filtres vidéo") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Préréglages intégrés",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BUILTIN_PRESETS.forEach { preset ->
                            val sel = state.filterPresetName == preset.name
                            Text(
                                preset.name,
                                color = if (sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .background(
                                        if (sel) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable { viewModel.applyPreset(preset) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (state.savedPresets.isNotEmpty()) {
                        Text(
                            "Mes préréglages", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        state.savedPresets.forEach { preset ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    preset.name,
                                    Modifier.weight(1f).clickable { viewModel.applyPreset(preset) }
                                        .padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                IconButton(onClick = { viewModel.deletePreset(preset.name) }) {
                                    Icon(
                                        Icons.Filled.Close, "Supprimer ${preset.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Réglage fin", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text("Luminosité ${state.filterBrightness.toInt()}",
                        style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = state.filterBrightness,
                        onValueChange = { viewModel.applyFilter(it, state.filterSaturation, state.filterHue, "perso") },
                        valueRange = -40f..40f,
                    )
                    Text("Saturation ${state.filterSaturation.toInt()}",
                        style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = state.filterSaturation,
                        onValueChange = { viewModel.applyFilter(state.filterBrightness, it, state.filterHue, "perso") },
                        valueRange = -40f..40f,
                    )
                    Text("Teinte ${state.filterHue.toInt()}°",
                        style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = state.filterHue,
                        onValueChange = { viewModel.applyFilter(state.filterBrightness, state.filterSaturation, it, "perso") },
                        valueRange = -60f..60f,
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Nom du nouveau préréglage") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            viewModel.saveCurrentAsPreset(presetName)
                            presetName = ""
                        },
                        enabled = presetName.isNotBlank(),
                    ) { Text("💾 Enregistrer le préréglage courant") }
                }
            },
        )
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
    TextButton(onClick = onBack) { Text("← Retour") }
}
