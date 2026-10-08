package dev.endlesssea.app.ui.player

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.endlesssea.app.ui.player.themes.fmtTime as formatTime
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.ui.player.themes.PlayerControlsActions
import dev.endlesssea.app.ui.player.themes.PlayerControlsState
import dev.endlesssea.app.ui.player.themes.ThemeProvider
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
        // §plein-ecran : la barre d'état (heure) et la barre de navigation
        // gênaient la vidéo — immersion totale, elles ne reviennent qu'au
        // balayage et se recachent toutes seules.
        hideSystemBars()
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
        lifecycleScope.launch { viewModel.persistPosition() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = androidx.core.view.WindowCompat
            .getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat
            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams
                    .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@kotlin.OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val isPlaying by viewModel.engine.isPlaying.collectAsState()
    val subtitleTracks by viewModel.engine.availableSubtitles.collectAsState()
    val audioTracks by viewModel.engine.availableAudio.collectAsState()

    val context = LocalContext.current
    val playerScope = androidx.compose.runtime.rememberCoroutineScope()
    // §sous-titres : ouverture d'un .srt/.vtt depuis le téléphone
    val subFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.engine.addExternalSubtitle(uri.toString(), "Fichier local")
        }
    }
    var textureView by remember { mutableStateOf<android.view.TextureView?>(null) }
    var videoSize by remember { mutableStateOf(VideoSize(0, 0)) }
    var showCcDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    /** §sheet-plus : panneau « Plus » (minuterie de veille + filtres vidéo). */
    var showMoreSheet by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var interactionVersion by remember { mutableIntStateOf(0) }
    // §sous-titres : recherche en ligne + ouverture d'un fichier local
    var showSubSearch by remember { mutableStateOf(false) }
    var subQuery by remember { mutableStateOf("") }
    var subLoading by remember { mutableStateOf(false) }
    var subResults by remember {
        androidx.compose.runtime.mutableStateOf<List<OnlineSubtitle>>(emptyList())
    }
    var slidingPos by remember { mutableStateOf<Float?>(null) }
    // §fit : 0 = contenir · 1 = remplir (zoom) · 2 = étirer (déforme)
    var zoomMode by remember { mutableIntStateOf(0) }
    /** §double-appui-continu : date du dernier saut par appui. */
    var lastSkipAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var landscapeNow by remember { mutableStateOf(true) } // bascule visuelle §orientation-lecteur
    /** §megaskip : éditeur des boutons de saut personnalisés (CRUD hors-ligne). */
    var showSkipDialog by remember { mutableStateOf(false) }
    /** §gestes-lecteur : pincer pour zoomer (1×–3×) + déplacement à deux doigts. */
    var pinchScale by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    DisposableEffect(viewModel.engine.player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }
        }
        viewModel.engine.player.addListener(listener)
        onDispose { viewModel.engine.player.removeListener(listener) }
    }

    // §placements / §theme-lecteur : réglages lus une fois pour tout l'habillage
    val progressPos by viewModel.progressPosition.collectAsState()
    val toolsPos by viewModel.toolsPosition.collectAsState()
    val screenBrightness by viewModel.screenBrightness.collectAsState()
    LaunchedEffect(screenBrightness) {
        (context as? android.app.Activity)?.window?.let { window ->
            window.attributes = window.attributes.apply { this.screenBrightness = screenBrightness / 100f }
        }
    }
    val megaSide by viewModel.megaSkipSide.collectAsState()
    val progressThickness by viewModel.progressThickness.collectAsState()
    val progressRounded by viewModel.progressRounded.collectAsState()
    val playerThemeName by viewModel.playerTheme.collectAsState()
    // §megaskip : segments en ligne + boutons personnalisés + stats
    val pinchZoomOn by viewModel.pinchZoomEnabled.collectAsState()
    val swapVB by viewModel.swapVolumeBrightness.collectAsState()
    // §gestes-lecteur (conversation 2) : seuils, pas et options des gestes
    // regroupés dans une configuration unique (plus de nombres magiques).
    val gestures = dev.endlesssea.player.GestureConfig(
        skipSeconds = state.skipSeconds,
        brightnessOnLeft = !swapVB,
        pinchZoomEnabled = pinchZoomOn,
    )
    val engineStats by viewModel.engineStats.collectAsState()
    // §theme-lecteur : chaque habillage change VRAIMENT la mise en page —
    // épaisseur et forme de la barre, voile du bas, taille du bouton central.
    val thumbSize by viewModel.seekThumb.collectAsState()
    val bufferThickness by viewModel.seekBuffer.collectAsState()
    val hideThumb by viewModel.seekHideThumb.collectAsState()
    LaunchedEffect(state.controlsVisible, isPlaying, state.locked, showMoreSheet, showPlaylist,
        showCcDialog, showAudioDialog, showFilterDialog, showQualityDialog, showSkipDialog,
        slidingPos, interactionVersion) {
        if (state.controlsVisible && isPlaying && !state.locked && !showMoreSheet && !showPlaylist &&
            !showCcDialog && !showAudioDialog && !showFilterDialog && !showQualityDialog && !showSkipDialog && slidingPos == null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.showControls(false)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) interactionVersion++
            }
        }
    }) {
        // TextureView directe sur le lecteur : requis pour voir les filtres vidéo (HslAdjustment)
        val renderMode by viewModel.videoRender.collectAsState()
        // §gestes-lecteur : zoom par pincement + déplacement à deux doigts —
        // appliqué à la surface vidéo (indépendant du mode « contenir/remplir »).
        val zoomLayer = Modifier.graphicsLayer(
            scaleX = pinchScale, scaleY = pinchScale,
            translationX = panX, translationY = panY,
        )
        if (renderMode == "surface") {
            // §rendu-vidéo : SurfaceView — rendu matériel direct (plus fluide,
            // compatible HDR / Android TV) ; les filtres vidéo sont inactifs.
            AndroidView(
                factory = { ctx ->
                    android.view.SurfaceView(ctx).also {
                        viewModel.engine.player.setVideoSurfaceView(it)
                        textureView = null
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .then(zoomLayer),
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    android.view.TextureView(ctx).also {
                        viewModel.engine.player.setVideoTextureView(it)
                        textureView = it
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .then(zoomLayer),
            )
        }

        // §gestes-lecteur : pincement (zoom 1×–3×) + déplacement à deux doigts.
        if (pinchZoomOn) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            pinchScale = (pinchScale * zoom).coerceIn(1f, gestures.maxPinchZoom)
                            if (pinchScale <= 1.01f) {
                                // Retour au cadre normal : on recentre.
                                panX = 0f; panY = 0f
                            } else {
                                panX += pan.x; panY += pan.y
                            }
                        }
                    },
            )
        }

        // Zoom (contenir ↔ remplir) appliqué par matrice sur la TextureView
        LaunchedEffect(zoomMode, videoSize) {
            val tv = textureView ?: return@LaunchedEffect
            val w = tv.width.takeIf { it > 0 } ?: return@LaunchedEffect
            val h = tv.height.takeIf { it > 0 } ?: return@LaunchedEffect
            val vw = videoSize.width.takeIf { it > 0 } ?: return@LaunchedEffect
            val vh = videoSize.height.takeIf { it > 0 } ?: return@LaunchedEffect
            val m = android.graphics.Matrix()
            val scaleCover = maxOf(w / vw.toFloat(), h / vh.toFloat())
            val scaleFit = minOf(w / vw.toFloat(), h / vh.toFloat())
            when (zoomMode) {
                1 -> {
                    val factor = if (scaleFit > 0f) scaleCover / scaleFit else 1f
                    m.setScale(factor, factor, w / 2f, h / 2f)
                }
                2 -> {
                    // étirer : on force le remplissage, quitte à déformer
                    val fx = if (scaleFit > 0f) (w / vw.toFloat()) / scaleFit else 1f
                    val fy = if (scaleFit > 0f) (h / vh.toFloat()) / scaleFit else 1f
                    m.setScale(fx, fy, w / 2f, h / 2f)
                }
            }
            tv.setTransform(m)
        }

        // ---- §gestes-lecteur (façon mpv-android) :
        // tap = contrôles · double-tap gauche/droite = ±skip · long-press = vitesse ×2
        // (restaurée au relâchement) · glisser horizontal = seek avec aperçu ·
        // glisser vertical = luminosité à gauche / volume à droite.
        val playerCtx = androidx.compose.ui.platform.LocalContext.current
        val playerActivity = playerCtx as? android.app.Activity
        val exo = viewModel.engine.player
        var gestureOverlay by remember { mutableStateOf<String?>(null) }
        var gestureSeq by remember { mutableStateOf(0) }
        var speedBoost = remember { false }
        fun flash(text: String) { gestureOverlay = text; gestureSeq += 1 }
        LaunchedEffect(gestureSeq) {
            val seq = gestureSeq
            if (gestureOverlay != null && !speedBoost) {
                kotlinx.coroutines.delay(1_200)
                if (gestureSeq == seq) gestureOverlay = null
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state.skipSeconds, state.locked) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (state.locked) return@detectTapGestures
                            // §double-appui-continu : tant qu'on reste dans la
                            // fenêtre de [GestureConfig.doubleTapWindowMs] après un
                            // saut, chaque appui enchaîne un nouveau saut
                            // (comportement YouTube) au lieu d'afficher/masquer
                            // les commandes.
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastSkipAt < gestures.doubleTapWindowMs) {
                                lastSkipAt = now
                                val half = size.width / 2
                                viewModel.jumpBy(
                                    if (offset.x < half) -state.skipSeconds else state.skipSeconds,
                                )
                            } else {
                                viewModel.toggleControls()
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!state.locked) {
                                val half = size.width / 2
                                val delta = if (offset.x < half) -state.skipSeconds else state.skipSeconds
                                // §double-message : jumpBy publie déjà state.skipFlash,
                                // inutile d'afficher un second bandeau par-dessus.
                                lastSkipAt = android.os.SystemClock.uptimeMillis()
                                viewModel.jumpBy(delta)
                            }
                        },
                        onLongPress = {
                            if (!state.locked) {
                                exo.setPlaybackSpeed(gestures.longPressSpeed)
                                speedBoost = true
                                gestureOverlay = "Vitesse ×%.0f".format(gestures.longPressSpeed)
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (speedBoost) {
                                exo.setPlaybackSpeed(1f)
                                speedBoost = false
                                gestureOverlay = null
                            }
                        },
                    )
                }
                .pointerInput(state.locked, state.durationMs) {
                    val audio = playerCtx.getSystemService(android.content.Context.AUDIO_SERVICE)
                        as android.media.AudioManager
                    val maxVol = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    var accX = 0f; var accY = 0f
                    var mode = 0           // 0 rien · 1 seek · 2 luminosité · 3 volume
                    var baseMs = 0L
                    var seekTarget = 0L
                    var startX = 0f
                    var baseVol = 0
                    var baseBright = 0.5f
                    detectDragGestures(
                        onDragStart = { start ->
                            accX = 0f; accY = 0f; mode = 0
                            baseMs = viewModel.uiState.value.positionMs
                            seekTarget = baseMs
                            startX = start.x
                            baseVol = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            baseBright = playerActivity?.window?.attributes?.screenBrightness
                                ?.takeIf { it in 0f..1f } ?: 0.5f
                        },
                        onDrag = { _, amount ->
                            if (state.locked) return@detectDragGestures
                            accX += amount.x; accY += amount.y
                            if (mode == 0 &&
                                (
                                    kotlin.math.abs(accX) > gestures.seekDeadZonePx ||
                                        kotlin.math.abs(accY) > gestures.seekDeadZonePx
                                    )
                            ) {
                                mode = if (kotlin.math.abs(accX) >= kotlin.math.abs(accY)) 1
                                else {
                                    // §gestes-lecteur : gauche = luminosité, droite = volume
                                    // (inversible depuis Réglages → Lecteur → Gestes).
                                    val leftSide = startX < size.width / 2f
                                    val brightnessSide =
                                        if (gestures.brightnessOnLeft) leftSide else !leftSide
                                    if (brightnessSide) 2 else 3
                                }
                            }
                            when (mode) {
                                1 -> {
                                    val dur = state.durationMs
                                    val span = 120_000L    // pleine largeur ≈ ±120 s
                                    seekTarget = baseMs +
                                        ((accX / size.width) * span).toLong()
                                    if (dur > 0) seekTarget = seekTarget.coerceIn(0L, dur)
                                    val d = (seekTarget - baseMs) / 1000
                                    flash("%+d s · %s".format(d, formatTime(seekTarget)))
                                }
                                2 -> {
                                    val b = (baseBright - accY / size.height).coerceIn(0.01f, 1f)
                                    playerActivity?.window?.let { w ->
                                        val lp = w.attributes
                                        lp.screenBrightness = b
                                        w.attributes = lp
                                    }
                                    flash("Luminosité ${(b * 100).toInt()} %")
                                }
                                3 -> {
                                    val v = (baseVol + (-accY / size.height) * maxVol)
                                        .toInt().coerceIn(0, maxVol)
                                    audio.setStreamVolume(
                                        android.media.AudioManager.STREAM_MUSIC, v, 0,
                                    )
                                    flash("Volume $v/$maxVol")
                                }
                            }
                        },
                        onDragEnd = {
                            if (mode == 1 && seekTarget != baseMs) exo.seekTo(seekTarget)
                            if (speedBoost) { exo.setPlaybackSpeed(1f); speedBoost = false }
                            gestureOverlay = null
                            mode = 0
                        },
                        onDragCancel = {
                            if (speedBoost) { exo.setPlaybackSpeed(1f); speedBoost = false }
                            gestureOverlay = null
                            mode = 0
                        },
                    )
                },
        )

        // ---- Bandeau flash du geste en cours (non bloquant, auto-effacé)
        gestureOverlay?.let { txt ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 88.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.66f),
            ) {
                Text(
                    txt,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }

        // ---- §marqueurs intro/outro : bouton « Passer » dans les plages éditées (vidéos locales)
        run {
            val markers = remember(state.playlistIndex) { PlayerLaunchStore.lastMarkers }
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
                                if (active) {
                                    Icon(
                                        Icons.Filled.Check, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }

        // Retour visuel du saut (double appui)
        state.skipFlash?.let { flash ->
            val forward = flash.startsWith("+")
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 120.dp),
                contentAlignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                ) {
                    Text(
                        if (forward) "$flash  ▶▶" else "◀◀  $flash",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }

        // ---- §sous-titres (conversation 1) : affichage réel des pistes.
        // Avant, les pistes étaient sélectionnables mais rien ne s'affichait :
        // Media3 exige une SubtitleView pour rendre les cues.
        var cues by remember { mutableStateOf<List<androidx.media3.common.text.Cue>>(emptyList()) }
        DisposableEffect(Unit) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    cues = cueGroup.cues
                }
            }
            viewModel.engine.player.addListener(listener)
            onDispose { viewModel.engine.player.removeListener(listener) }
        }
        if (cues.isNotEmpty()) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    androidx.media3.ui.SubtitleView(ctx).apply {
                        setApplyEmbeddedStyles(false)
                        setStyle(viewModel.engine.captionStyle())
                        setBottomPaddingFraction(0.10f)
                    }
                },
                update = { view ->
                    view.setStyle(viewModel.engine.captionStyle())
                    view.setCues(cues)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (state.controlsVisible) 104.dp else 20.dp),
            )
        }

        // ---- §theme-lecteur (conversations 1 & 2) : les commandes viennent du
        // thème choisi — 8 skins complets (mise en page, formes, accents),
        // pas un simple changement de couleur.
        val activeTheme = ThemeProvider.of(playerThemeName)
        if (state.controlsVisible || state.locked) {
            val controlsState = PlayerControlsState(
                title = state.title,
                subtitle = state.links.getOrNull(state.currentLinkIndex)
                    ?.let { "${it.server} — ${it.quality.label}" } ?: "",
                playing = isPlaying,
                locked = state.locked,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                dragFraction = slidingPos,
                speed = state.speed,
                zoomMode = zoomMode,
                hasPrev = state.hasPrev,
                hasNext = state.hasNext,
                hasLinks = state.links.isNotEmpty(),
                filterActive = state.filterPresetName != "none" && state.filterPresetName != "Aucun",
                statsVisible = state.statsVisible,
                skipLoading = state.skipLoading,
                megaSkipSeconds = state.megaSkipSeconds,
                megaSkipLeft = megaSide == "left",
                activeSkip = state.activeSkip,
                skipCountdown = state.skipCountdown,
                customSkips = state.skipButtons,
                progressOnTop = progressPos == "top",
                toolsOnTop = toolsPos == "top",
                progressThickness = progressThickness,
                bufferedPositionMs = state.bufferedPositionMs,
                thumbSize = thumbSize,
                bufferThickness = bufferThickness,
                autoHideThumb = hideThumb,
                progressRounded = progressRounded,
            )
            val controlsActions = PlayerControlsActions(
                onBack = onBack,
                onTogglePlay = { viewModel.togglePlayback() },
                onPrev = { viewModel.playQueueOffset(-1) },
                onNext = { viewModel.playQueueOffset(1) },
                onDrag = { slidingPos = it; interactionVersion++ },
                onDragFinished = { fraction ->
                    viewModel.engine.player.seekTo(
                        (fraction * state.durationMs.coerceAtLeast(1L)).toLong(),
                    )
                    slidingPos = null
                },
                onJumpRelative = { viewModel.jumpBy(it) },
                onOpenQuality = { showQualityDialog = true },
                onOpenSubtitles = { showCcDialog = true },
                onOpenAudio = { showAudioDialog = true },
                onToggleLock = { viewModel.toggleLock() },
                onToggleOrientation = {
                    val act = context as? android.app.Activity
                    landscapeNow = !landscapeNow
                    viewModel.setOrientationPreference(if (landscapeNow) "landscape" else "portrait")
                    act?.requestedOrientation = if (!landscapeNow) {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                },
                onCycleZoom = { zoomMode = (zoomMode + 1) % 3 },
                onCycleSpeed = {
                    val next = SPEED_STEPS[(SPEED_STEPS.indexOf(state.speed) + 1) % SPEED_STEPS.size]
                    viewModel.setSpeed(next)
                },
                onOpenFilters = { showFilterDialog = true },
                onToggleStats = { viewModel.toggleStats() },
                onRefreshSkip = { viewModel.refreshSkip() },
                onOpenMore = { showMoreSheet = true },
                onOpenPlaylist = { showPlaylist = true },
                onMegaJump = { viewModel.megaJump(state.megaSkipSeconds) },
                onOpenSkipEditor = { showSkipDialog = true },
                onSkipSegment = { viewModel.skipActive() },
                onCustomSkip = { button -> viewModel.customSkip(button) },
            )
            if (state.locked) {
                Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                    dev.endlesssea.app.ui.player.themes.SkinIconButton(
                        Icons.Default.LockOpen, "Déverrouiller", { viewModel.toggleLock() },
                        background = Color.Black.copy(alpha = 0.6f),
                    )
                }
            } else Column(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
                activeTheme.TopControls(controlsState, controlsActions)
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    dev.endlesssea.app.ui.player.themes.PlayerBarContent(
                        controlsState, controlsActions,
                        activeTheme.accent ?: MaterialTheme.colorScheme.primary, top = true,
                    )
                }
            }
            if (!state.locked) {
                activeTheme.CenterControls(
                    controlsState, controlsActions,
                    Modifier.align(Alignment.Center),
                )
                activeTheme.BottomControls(
                    controlsState, controlsActions,
                    Modifier.align(Alignment.BottomCenter),
                )
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

        // ---- §stats : surimpression technique (résolution, débit, codec, fps)
        if (state.statsVisible) {
            PlayerStatsOverlay(
                stats = engineStats,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                speed = state.speed,
                segmented = state.skipSegments.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 96.dp),
            )
        }
    }

    if (showPlaylist) {
        PlaylistSheet(state, onDismiss = { showPlaylist = false }, onSelect = { index ->
            viewModel.playPlaylistIndex(index)
            showPlaylist = false
        })
    }

    // ---- §megaskip : éditeur des boutons de saut personnalisés
    if (showSkipDialog) {
        dev.endlesssea.app.ui.player.CustomSkipDialog(
            buttons = state.skipButtons,
            onAdd = { label, seconds -> viewModel.addSkipButton(label, seconds) },
            onUpdate = { viewModel.updateSkipButton(it) },
            onDelete = { viewModel.deleteSkipButton(it) },
            onDismiss = { showSkipDialog = false },
        )
    }

    // ---- §sheet-plus : panneau « Plus » façon lecteurs pro — minuterie de veille
    // (Arrêt/15/30/45/60 min) + filtres vidéo en grille, tout au même endroit.
    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Paramètres du lecteur",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))

                // §reglages-rapides : les MÊMES préférences que Réglages → Lecteur,
                // accessibles sans quitter la vidéo (elles sont synchronisées).
                Text(
                    "Réglages du lecteur",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Luminosité au démarrage : $screenBrightness %")
                Slider(value = screenBrightness.toFloat(), onValueChange = { viewModel.setScreenBrightness(it.toInt()) }, valueRange = 10f..100f)
                TextButton(onClick = { showMoreSheet = false; showPlaylist = true }) { Text("Playlist") }
                Row {
                    TextButton(onClick = { showMoreSheet = false; showQualityDialog = true }) { Text("Qualité") }
                    TextButton(onClick = { showMoreSheet = false; showAudioDialog = true }) { Text("Audio") }
                    TextButton(onClick = { viewModel.toggleLock(); showMoreSheet = false }) { Text("Verrouiller") }
                }
                TextButton(onClick = { zoomMode = (zoomMode + 1) % 3 }) { Text("Cadrage : ${dev.endlesssea.app.ui.player.themes.zoomLabel(zoomMode)}") }
                TextButton(onClick = { showMoreSheet = false; showSkipDialog = true }) { Text("Configurer les sauts") }
                Text("Épaisseur de la barre : $progressThickness dp",
                    style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = progressThickness.toFloat(),
                    onValueChange = { viewModel.setProgressThickness(it.toInt()) },
                    valueRange = 2f..8f,
                )
                TextButton(onClick = { viewModel.setProgressRounded(!progressRounded) }) {
                    Text(if (progressRounded) "Barre arrondie" else "Barre à angles droits")
                }
                Text("Taille du curseur : $thumbSize dp")
                Slider(value = thumbSize.toFloat(), onValueChange = { viewModel.setSeekThumb(it.toInt()) }, valueRange = 8f..20f)
                Text("Épaisseur du buffer : $bufferThickness dp")
                Slider(value = bufferThickness.toFloat(), onValueChange = { viewModel.setSeekBuffer(it.toInt()) }, valueRange = 2f..8f)
                TextButton(onClick = { viewModel.setSeekHideThumb(!hideThumb) }) { Text(if (hideThumb) "Afficher le curseur au repos" else "Masquer le curseur au repos") }
                Text("Thème du lecteur", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    dev.endlesssea.app.di.AppPrefs.PLAYER_THEMES.forEach { (key, v) ->
                        FilterChip(
                            selected = playerThemeName == key,
                            onClick = { viewModel.setPlayerTheme(key) },
                            label = { Text(v.second, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                // §upscale : on parle d'ÉCHELLE (x1.5, x2), jamais de « 720p/1080p »
                Text(
                    "Agrandissement de l'image : x%.2f".format(state.videoScale),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1f to "Natif", 1.25f to "x1.25", 1.5f to "x1.5", 2f to "x2")
                        .forEach { (value, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(state.videoScale - value) < 0.01f,
                                onClick = { viewModel.setVideoScale(value) },
                                label = { Text(label, maxLines = 1, softWrap = false) },
                            )
                        }
                }
                Text(
                    "Renforcement des contours : %d %%".format((state.videoSharpen * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = state.videoSharpen,
                    onValueChange = { viewModel.setVideoSharpen(it) },
                    valueRange = 0f..2f,
                )
                Text("Style d'image", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "none" to "Aucun", "anime" to "Anime", "anime_fort" to "Anime fort",
                        "net" to "Netteté", "eclat" to "Éclat", "doux" to "Anti-grain",
                        "cinema" to "Cinéma", "nuit" to "Nuit",
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = state.enhance == key,
                            onClick = { viewModel.setEnhance(key) },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // §sous-titres-en-ligne / fichier local
                Text(
                    "Sous-titres",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = {
                        showMoreSheet = false
                        showSubSearch = true
                    }) { Text("Chercher en ligne") }
                    TextButton(onClick = {
                        showMoreSheet = false
                        subFilePicker.launch(arrayOf("*/*"))
                    }) { Text("Ouvrir un fichier .srt") }
                }
                Spacer(Modifier.height(14.dp))

                // ---- Minuterie de veille
                Text(
                    "Minuterie de veille",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.sleepEndAt != null) {
                    Text(
                        "Pause programmée ${"%.0f".format(((state.sleepEndAt ?: 0L) - System.currentTimeMillis()) / 60_000f)} min restantes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0 to "Arrêt", 15 to "15'", 30 to "30'", 45 to "45'", 60 to "60'").forEach { (min, label) ->
                        val remain = state.sleepEndAt
                        val sel = if (min == 0) remain == null else remain != null
                        FilterChip(
                            selected = sel,
                            onClick = { viewModel.scheduleSleep(min) },
                            label = { Text(label, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                // ---- Filtres vidéo en grille (12 préréglages)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Filtres vidéo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BUILTIN_PRESETS.forEach { preset ->
                        val sel = state.filterPresetName == preset.name
                        FilterChip(
                            selected = sel,
                            onClick = { viewModel.applyPreset(preset) },
                            label = { Text(preset.name, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    showMoreSheet = false
                    showFilterDialog = true
                }) { Text("Réglage fin & préréglages personnels…") }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    // ---- Toast lecteur bref (§minuterie : pause programmée, etc.)
    LaunchedEffect(state.toast) {
        state.toast?.let {
            kotlinx.coroutines.delay(2_400)
            viewModel.clearToast()
        }
    }
    state.toast?.let {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 100.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.66f),
            ) {
                Text(
                    it, color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }

    // ---- §sous-titres-en-ligne : recherche OpenSubtitles + téléchargement
    if (showSubSearch) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            if (subQuery.isBlank()) subQuery = state.title
        }
        AlertDialog(
            onDismissRequest = { showSubSearch = false },
            confirmButton = {
                TextButton(onClick = {
                    subLoading = true
                    playerScope.launch {
                        subResults = OnlineSubtitles.search(subQuery)
                        subLoading = false
                    }
                }) { Text("Chercher") }
            },
            dismissButton = {
                TextButton(onClick = { showSubSearch = false }) { Text("Fermer") }
            },
            title = { Text("Sous-titres en ligne") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    androidx.compose.material3.OutlinedTextField(
                        value = subQuery,
                        onValueChange = { subQuery = it },
                        label = { Text("Titre du film / de l'épisode") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (subLoading) {
                        CircularProgressIndicator()
                    } else if (subResults.isEmpty()) {
                        Text(
                            "Aucun résultat pour l'instant — lancez une recherche.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            Modifier.heightIn(max = 320.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        ) {
                            subResults.forEach { sub ->
                                Column(
                                    Modifier.fillMaxWidth().clickable {
                                        showSubSearch = false
                                        playerScope.launch {
                                            val path = OnlineSubtitles.download(context, sub)
                                            if (path != null) {
                                                viewModel.engine.addExternalSubtitle(path, sub.lang)
                                            }
                                        }
                                    }.padding(vertical = 8.dp),
                                ) {
                                    Text(sub.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                    Text(
                                        sub.lang,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
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
                    // §filtres-video : réglages avancés demandés (contraste, gamma,
                    // netteté, température) — appliqués en direct, sans quitter.
                    Text("Contraste ${"%.2f".format(state.filterContrast)}",
                        style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = state.filterContrast,
                        onValueChange = {
                            viewModel.applyAdvanced(it, state.filterGamma, state.filterSharp, state.filterTemp)
                        },
                        valueRange = 0.5f..2f,
                    )
                    Text("Gamma ${"%.2f".format(state.filterGamma)}",
                        style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = state.filterGamma,
                        onValueChange = {
                            viewModel.applyAdvanced(state.filterContrast, it, state.filterSharp, state.filterTemp)
                        },
                        valueRange = 0.5f..2f,
                    )
                    Text("Netteté ${(state.filterSharp * 100).toInt()} %",
                        style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = state.filterSharp,
                        onValueChange = {
                            viewModel.applyAdvanced(state.filterContrast, state.filterGamma, it, state.filterTemp)
                        },
                        valueRange = 0f..1f,
                    )
                    Text(
                        "Température " + when {
                            state.filterTemp > 0.05f -> "chaude"
                            state.filterTemp < -0.05f -> "froide"
                            else -> "neutre"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = state.filterTemp,
                        onValueChange = {
                            viewModel.applyAdvanced(state.filterContrast, state.filterGamma, state.filterSharp, it)
                        },
                        valueRange = -1f..1f,
                    )
                    Text(
                        "Amélioration de l'image",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        "Traitements légers (pas d'upscale IA : ça ramerait sur mobile) — " +
                            "contraste local, éclat, lissage du grain, rendu cinéma ou mode nuit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            "none" to "Aucun", "anime" to "Anime",
                            "anime_fort" to "Anime fort", "net" to "Netteté douce",
                            "eclat" to "Éclat", "doux" to "Anti-grain",
                            "cinema" to "Cinéma", "nuit" to "Nuit",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = state.enhance == key,
                                onClick = { viewModel.setEnhance(key) },
                                label = { Text(label, maxLines = 1, softWrap = false) },
                            )
                        }
                    }
                    TextButton(onClick = {
                        viewModel.applyAdvanced(1f, 1f, 0f, 0f)
                        viewModel.setEnhance("none")
                        viewModel.applyFilter(0f, 0f, 0f, "none")
                    }) { Text("Tout réinitialiser") }
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Nom du nouveau préréglage", maxLines = 1, softWrap = false) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(
                        onClick = {
                            viewModel.saveCurrentAsPreset(presetName)
                            presetName = ""
                        },
                        enabled = presetName.isNotBlank(),
                    ) { Text("Enregistrer le préréglage courant") }
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
                    // §sous-titres : décalage ± des pistes externes (pas de 0,5 s).
                    val delayState by viewModel.subtitleDelayMs.collectAsState()
                    Text(
                        "Décalage : " + (if (delayState >= 0) "+" else "−") +
                            "%.1f s".format(kotlin.math.abs(delayState) / 1000.0),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { viewModel.shiftSubtitle(-500) }) { Text("− 0,5 s") }
                        TextButton(onClick = { viewModel.shiftSubtitle(500) }) { Text("+ 0,5 s") }
                        TextButton(onClick = { viewModel.resetSubtitleDelay() }) { Text("Recaler") }
                    }
                    Text(
                        "Le décalage s'applique aux sous-titres externes (fichier local ou téléchargé) ; " +
                            "les pistes intégrées à la vidéo ne peuvent pas être décalées.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
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
                    // §audio : boost du volume jusqu'à 200 % (×2, +6 dB).
                    val boostState by viewModel.audioBoostPercent.collectAsState()
                    Text("Boost audio : $boostState %", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = boostState.toFloat(),
                        onValueChange = { viewModel.setAudioBoost(it.toInt()) },
                        valueRange = 100f..200f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Au-delà de 100 %, le son est amplifié (LoudnessEnhancer) sans ré-encodage.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
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
    TextButton(onClick = onBack) { Text("Retour") }
}


/**
 * §stats — surimpression technique demandée dans la conversation « lecteur
 * complet » : résolution, débit, codec, images/s, images perdues, tampon,
 * vitesse, position et nombre de segments Megaskip connus.
 */
@Composable
private fun PlayerStatsOverlay(
    stats: dev.endlesssea.player.PlayerStats,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    segmented: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.60f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "STATISTIQUES",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
            )
            listOf(
                "Résolution" to stats.resolution,
                "Débit" to stats.bitrateLabel,
                "Images/s" to stats.fpsLabel,
                "Codec" to stats.codec.ifBlank { "—" },
                "Perdues" to stats.droppedFrames.toString(),
                "Tampon" to stats.bufferedLabel,
                "Vitesse" to "%.2f×".format(speed),
                "Position" to "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                "Segments" to segmented.toString(),
            ).forEach { (label, value) ->
                Row {
                    Text(
                        label,
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(84.dp),
                    )
                    Text(
                        value,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
