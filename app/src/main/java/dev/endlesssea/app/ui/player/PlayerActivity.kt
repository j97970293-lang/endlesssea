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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.ScreenRotation
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.media3.common.VideoSize
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import dev.endlesssea.app.ui.theme.EndlessSeaTheme

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    @Inject lateinit var prefs: dev.endlesssea.app.di.AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // §orientation-lecteur : horizontal par défaut, modifiable (bouton rotation + réglage)
        requestedOrientation = if (prefs.playerOrientation.value == "portrait") {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
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
    // §couleurs-lecteur : demande « noir et blanc, vibrant, viv anime, autres »
    VideoFilterPreset("Noir & blanc", 0f, -100f, 0f),
    VideoFilterPreset("N&B doux", -4f, -85f, 0f),
    VideoFilterPreset("Vibrant", 4f, 40f, 0f),
    VideoFilterPreset("Anime vif", 6f, 55f, 4f),
    VideoFilterPreset("Pastel", 8f, -25f, 0f),
    VideoFilterPreset("Sepia rétro", 2f, -35f, 18f),
    VideoFilterPreset("Sombre", -14f, 10f, 0f),
    VideoFilterPreset("Froid", 0f, 8f, -14f),
    VideoFilterPreset("Chaud", 2f, 6f, 14f),
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
    var textureView by remember { mutableStateOf<android.view.TextureView?>(null) }
    var videoSize by remember { mutableStateOf(VideoSize(0, 0)) }
    var showCcDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(1f) }
    var slidingPos by remember { mutableStateOf<Float?>(null) }
    var zoomFit by remember { mutableStateOf(true) } // true = contenir, false = remplir
    var showQualityDialog by remember { mutableStateOf(false) }
    var landscapeNow by remember { mutableStateOf(true) } // bascule visuelle §orientation-lecteur

    LaunchedEffect(Unit) {
        viewModel.engine.player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }
        })
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // TextureView directe sur le lecteur : requis pour voir les filtres vidéo (HslAdjustment)
        AndroidView(
            factory = { ctx ->
                android.view.TextureView(ctx).also {
                    viewModel.engine.player.setVideoTextureView(it)
                    textureView = it
                }
            },
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(),
        )

        // Zoom (contenir ↔ remplir) appliqué par matrice sur la TextureView
        LaunchedEffect(zoomFit, videoSize) {
            val tv = textureView ?: return@LaunchedEffect
            val w = tv.width.takeIf { it > 0 } ?: return@LaunchedEffect
            val h = tv.height.takeIf { it > 0 } ?: return@LaunchedEffect
            val vw = videoSize.width.takeIf { it > 0 } ?: return@LaunchedEffect
            val vh = videoSize.height.takeIf { it > 0 } ?: return@LaunchedEffect
            val m = android.graphics.Matrix()
            if (!zoomFit) {
                val scale = maxOf(w / vw.toFloat(), h / vh.toFloat())
                val scaleFit = minOf(w / vw.toFloat(), h / vh.toFloat())
                val factor = if (scaleFit > 0f) scale / scaleFit else 1f
                m.setScale(factor, factor, w / 2f, h / 2f)
            }
            tv.setTransform(m)
        }

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

        // ---- §marqueurs intro/outro : bouton « Passer » dans les plages éditées (vidéos locales)
        run {
            val markers = remember { PlayerLaunchStore.lastMarkers }
            if (markers.hasAny) {
                val posSec = state.positionMs / 1000
                val introStart = markers.introStartSec
                val introEnd = markers.introEndSec
                val inIntro = introStart != null && introEnd != null && posSec >= introStart && posSec < introEnd
                val outroFrom = markers.outroStartSec
                val inOutro = outroFrom != null && posSec >= outroFrom &&
                    state.durationMs - state.positionMs > 5_000
                if (inIntro || inOutro) {
                    val targetMs = if (inIntro) (introEnd ?: 0) * 1000L else state.durationMs
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 64.dp, end = 14.dp)
                            .clickable {
                                viewModel.engine.player.seekTo(targetMs.coerceAtMost(state.durationMs))
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                    ) {
                        Text(
                            if (inIntro) "Passer l'intro ⏭" else "Passer le générique ⏭",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        // ---- §auto-skip : saut automatique des marqueurs (une fois par plage)
        run {
            var introDoneAt by remember { mutableStateOf(-1L) }
            var outroDoneAt by remember { mutableStateOf(-1L) }
            LaunchedEffect(state.positionMs, state.autoSkipMarkers) {
                if (!state.autoSkipMarkers) return@LaunchedEffect
                val m = PlayerLaunchStore.lastMarkers
                if (!m.hasAny) return@LaunchedEffect
                val pos = state.positionMs / 1000
                val iS = m.introStartSec; val iE = m.introEndSec
                if (iS != null && iE != null && pos >= iS && pos < iE && introDoneAt < iS.toLong()) {
                    introDoneAt = iE.toLong()
                    viewModel.engine.player.seekTo(iE * 1000L)
                }
                val oS = m.outroStartSec
                if (oS != null && pos >= oS && outroDoneAt < 0 && state.durationMs - state.positionMs > 5_000) {
                    outroDoneAt = pos
                    viewModel.engine.player.seekTo(state.durationMs)
                }
            }
        }

        // ---- §qualité-lecteur : dialogue des liens (serveur + qualité) pendant la lecture
        if (showQualityDialog && state.links.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showQualityDialog = false },
                confirmButton = {
                    TextButton(onClick = { showQualityDialog = false }) { Text("Fermer") }
                },
                title = { Text("Qualité / serveur") },
                text = {
                    Column {
                        state.links.forEachIndexed { i, link ->
                            val active = i == state.currentLinkIndex
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchQuality(i)
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${link.server} — ${link.quality.label}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (link.streamType.name != "DIRECT_FILE") {
                                        Text(
                                            link.streamType.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (active) Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
            )
        }

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
                if (state.links.size > 1) {
                    IconButton(onClick = { showQualityDialog = true }) {
                        Icon(Icons.Filled.HighQuality, "Qualité / serveur", tint = Color.White)
                    }
                }
                IconButton(onClick = {
                    val act = context as? android.app.Activity
                    landscapeNow = !landscapeNow
                    viewModel.setOrientationPreference(if (landscapeNow) "landscape" else "portrait")
                    act?.requestedOrientation = if (!landscapeNow) {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }) {
                    Icon(
                        Icons.Filled.ScreenRotation, "Basculer portrait/paysage",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { zoomFit = !zoomFit }) {
                    Icon(
                        Icons.Filled.ZoomIn, "Zoom plein écran",
                        tint = if (zoomFit) Color.White else MaterialTheme.colorScheme.primary,
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
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
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
                        // §mégaskip : grand saut ±85s (réglable) autour du play
                        IconButton(onClick = { viewModel.megaJump(-state.megaSkipSeconds) }) {
                            Text("−${state.megaSkipSeconds}", color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { viewModel.jumpBy(-state.skipSeconds) }) {
                            Text("−${state.skipSeconds}s", color = Color.White,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(
                            onClick = { if (isPlaying) viewModel.engine.pause() else viewModel.engine.play() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "Lecture/Pause", tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        IconButton(onClick = { viewModel.jumpBy(state.skipSeconds) }) {
                            Text("+${state.skipSeconds}s", color = Color.White,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = { viewModel.megaJump(state.megaSkipSeconds) }) {
                            Text("+${state.megaSkipSeconds}", color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelMedium)
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
