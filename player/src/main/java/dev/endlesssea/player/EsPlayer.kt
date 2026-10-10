package dev.endlesssea.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import dev.endlesssea.extensions.api.model.StreamType
import dev.endlesssea.extensions.api.model.SubtitleTrack
import dev.endlesssea.extensions.api.model.VideoLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Media3/ExoPlayer implementation of [PlayerEngine] (docs/en/07).
 *
 * - extension headers (Referer/UA/cookies post-CAPTCHA) injected via OkHttpDataSource
 * - external sidecar subtitles attached as SubtitleConfigurations
 * - position polled every second → [positionMs] (app persists it every 5 s, §7)
 */
@UnstableApi
class EsPlayer(
    context: Context,
    httpClient: OkHttpClient,
    private val scope: CoroutineScope,
) : PlayerEngine {

    private val appContext = context.applicationContext

    private val httpFactory = OkHttpDataSource.Factory(httpClient)

    /** §lecture-locale : DefaultDataSource choisit selon le schéma (http→OkHttp, content/file→natif).
     * Auparavant tout passait par OkHttpDataSource → les vidéos locales (content://) échouaient. */
    private val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(appContext, httpFactory)

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(appContext).setDataSourceFactory(dataSourceFactory))
        // Mobile-tuned buffers for ≤ 2 GB RAM devices (doc 07 §5)
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
                .build()
        )
        .setSeekForwardIncrementMs(10_000)
        .setSeekBackIncrementMs(10_000)
        .build()

    private val liveVideoEffect = LiveVideoEffect()
    private var videoEffectsEnabled = true
    private val _sourceAspect = MutableStateFlow(16f / 9f)
    val sourceAspect: StateFlow<Float> = _sourceAspect
    private fun publishSourceAspect() {
        val format = player.videoFormat ?: return
        if (format.width <= 0 || format.height <= 0) return
        val ratio = format.width * format.pixelWidthHeightRatio / format.height
        _sourceAspect.value = if (format.rotationDegrees == 90 || format.rotationDegrees == 270) 1f / ratio else ratio
    }
    private val _playbackRequested = MutableStateFlow(false)
    /** Transport icon follows intent, including buffering, rather than rendered frames. */
    val playbackRequested: StateFlow<Boolean> = _playbackRequested
    private fun publishPlaybackIntent() {
        _playbackRequested.value = playbackButtonShowsPause(player.playWhenReady, player.playbackState, player.playerError != null)
    }
    private val _isPlaying = MutableStateFlow(false)
    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _availableSubtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    private val _availableAudio = MutableStateFlow<List<AudioTrackInfo>>(emptyList())

    /** §stats : surimpression technique (résolution, codec, débit, images perdues…). */
    private val _stats = MutableStateFlow(PlayerStats())

    /** §sous-titres : décalage courant (ms) de la piste externe active. */
    private val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs

    /** §audio : boost de 100 % (normal) à 200 % (×2, +6 dB). */
    private val _audioBoostPercent = MutableStateFlow(100)
    val audioBoostPercent: StateFlow<Int> = _audioBoostPercent

    /** Pistes externes rattachées à la volée (décalage possible). */
    private data class ExtSub(
        val original: String,
        val label: String,
        var offsetMs: Long = 0L,
        var current: String = original,
    )

    private val externalSubs = mutableListOf<ExtSub>()

    /** §audio : LoudnessEnhancer branché sur la session audio du lecteur. */
    private var loudness: android.media.audiofx.LoudnessEnhancer? = null

    override val isPlaying: StateFlow<Boolean> = _isPlaying
    override val positionMs: StateFlow<Long> = _positionMs
    override val durationMs: StateFlow<Long> = _durationMs
    override val availableSubtitles: StateFlow<List<SubtitleTrack>> = _availableSubtitles
    override val availableAudio: StateFlow<List<AudioTrackInfo>> = _availableAudio

    val stats: StateFlow<PlayerStats> = _stats

    var subtitleStyle: SubtitleStyle = SubtitleStyle()
        private set

    private var currentIndex = 0
    private var sourceLinks: List<VideoLink> = emptyList()
    private var playbackGeneration = 0L
    private val _playbackEnded = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Playback generation that reached STATE_ENDED, used to advance a queue exactly once. */
    val playbackEnded: SharedFlow<Long> = _playbackEnded.asSharedFlow()
    val currentPlaybackGeneration: Long get() = playbackGeneration

    private val relativeSeekTarget = RelativeSeekTarget()
    private var relativeSeekJob: kotlinx.coroutines.Job? = null

    init {
        // Media3 1.5 creates its video graph only on the first renderer enable (or reset).
        // Install ONE permanent shader before prepare; later edits update uniforms only.
        player.setVideoEffects(listOf(liveVideoEffect))
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                cancelRelativeSeek()
                if (reason == Player.DISCONTINUITY_REASON_SEEK) playbackGeneration += 1
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { publishPlaybackIntent() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                publishPlaybackIntent()
                if (
                    playbackState == Player.STATE_ENDED &&
                    player.playbackState == Player.STATE_ENDED &&
                    player.mediaItemCount > 0
                ) {
                    _playbackEnded.tryEmit(playbackGeneration)
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { publishPlaybackIntent() }
            override fun onIsPlayingChanged(playing: Boolean) { _isPlaying.value = playing }
            override fun onTracksChanged(tracks: Tracks) {
                publishTracks(tracks)
                publishSourceAspect()
                // §stats : format vidéo courant (codec, débit, images/s)
                val format = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
                    .mapNotNull { group -> group.mediaTrackGroup.getFormat(0) }
                    .firstOrNull()
                if (format != null) {
                    _stats.value = _stats.value.copy(
                        codec = format.codecs ?: format.sampleMimeType ?: "",
                        bitrate = format.bitrate ?: 0,
                        frameRate = format.frameRate ?: 0f,
                        width = format.width.takeIf { it > 0 } ?: _stats.value.width,
                        height = format.height.takeIf { it > 0 } ?: _stats.value.height,
                    )
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                publishSourceAspect()
                _stats.value = _stats.value.copy(
                    width = videoSize.width, height = videoSize.height,
                    pixelRatio = videoSize.pixelWidthHeightRatio,
                )
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _stats.value = _stats.value.copy(speed = playbackParameters.speed)
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // §audio : la session peut changer après un changement de piste
                // ou de lien — le boost doit être rebranché.
                loudness?.release()
                loudness = null
                applyAudioBoost()
            }
        })
        // §stats : images perdues (compteur du décodeur, sans coût mesurable)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                _stats.value = _stats.value.copy(droppedFrames = droppedFrames)
            }
        })
        scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (player.isPlaying) {
                    _positionMs.value = player.currentPosition
                    _durationMs.value = player.duration.coerceAtLeast(0)
                }
                _stats.value = _stats.value.copy(
                    bufferedMs = player.bufferedPosition,
                    playbackState = player.playbackState,
                )
                delay(1_000)
            }
        }
    }

    override fun prepare(links: List<VideoLink>, startPositionMs: Long, startIndex: Int) {
        if (links.isEmpty()) {
            sourceLinks = emptyList()
            currentIndex = 0
            playbackGeneration += 1
            _availableSubtitles.value = emptyList()
            player.clearMediaItems()
            error("Aucun lien vidéo disponible")
        }
        sourceLinks = links.toList()
        activateLink(startIndex.coerceIn(0, sourceLinks.lastIndex), startPositionMs)
    }

    /** A stream link is an alternative source, not the next item in a playlist. */
    fun selectLink(index: Int, positionMs: Long) {
        if (index !in sourceLinks.indices || index == currentIndex) return
        activateLink(index, positionMs)
    }

    private fun activateLink(index: Int, positionMs: Long) {
        val link = sourceLinks[index]
        currentIndex = index
        playbackGeneration += 1
        prepareWithHeaders(link.headers)
        val item = MediaItem.Builder()
            .setMediaId("${link.server}:${link.quality.label}")
            .setUri(link.url)
            .setMimeType(
                when (link.streamType) {
                    StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    StreamType.DASH -> MimeTypes.APPLICATION_MPD
                    else -> MimeTypes.VIDEO_MP4 // progressive default; Media3 sniffs anyway
                }
            )
            .setSubtitleConfigurations(link.subtitles.map { it.toConfiguration() })
            .build()
        _availableSubtitles.value = link.subtitles
        player.setMediaItem(item, positionMs.coerceAtLeast(0L))
        player.prepare()
    }

    /**
     * §sous-titres-externes : attache une piste (fichier local ou URL) à la
     * lecture en cours, sans perdre la position.
     */
    fun addExternalSubtitle(uri: String, label: String) {
        val current = player.currentMediaItem ?: return
        val pos = player.currentPosition
        // §sous-titres : le décalage par défaut de l'utilisateur s'applique
        // immédiatement à la piste qui vient d'être ajoutée.
        val wanted = _subtitleDelayMs.value
        val file = if (wanted != 0L) SubtitleShift.shiftToCache(appContext, uri, wanted) ?: uri else uri
        externalSubs.removeAll { it.original == uri }
        externalSubs.add(ExtSub(original = uri, label = label, offsetMs = wanted, current = file))
        val subs = current.localConfiguration?.subtitleConfigurations.orEmpty() +
            subtitleConfig(file, label, selected = true)
        val item = current.buildUpon().setSubtitleConfigurations(subs).build()
        player.setMediaItem(item, pos)
        player.prepare()
        player.play()
    }

    /**
     * §sous-titres (conversation 1) — décale la piste externe active de
     * [deltaMs] (bornes ±30 s). Le fichier décalé est réécrit dans le cache et
     * rattaché à la lecture en cours, qui reprend à la même position.
     * Renvoie le décalage appliqué (ms).
     */
    fun shiftSubtitle(deltaMs: Long): Long {
        val sub = externalSubs.lastOrNull() ?: run {
            _subtitleDelayMs.value = (_subtitleDelayMs.value + deltaMs).coerceIn(-30_000L, 30_000L)
            return _subtitleDelayMs.value
        }
        val next = (sub.offsetMs + deltaMs).coerceIn(-30_000L, 30_000L)
        val shifted = if (next == 0L) {
            sub.original
        } else {
            SubtitleShift.shiftToCache(appContext, sub.original, next) ?: sub.current
        }
        sub.offsetMs = next
        sub.current = shifted
        _subtitleDelayMs.value = next
        reattachSubtitles()
        return next
    }

    /** Force le décalage (sans le cumuler) — utilisé au démarrage. */
    fun setSubtitleDelay(ms: Long) {
        val target = ms.coerceIn(-30_000L, 30_000L)
        val delta = target - _subtitleDelayMs.value
        if (delta != 0L) shiftSubtitle(delta) else _subtitleDelayMs.value = target
    }

    /** Reconstruit les pistes externes (le fichier décalé remplace l'original). */
    private fun reattachSubtitles() {
        val current = player.currentMediaItem ?: return
        val pos = player.currentPosition
        val ours = (externalSubs.map { it.current } + externalSubs.map { it.original }).toSet()
        val kept = current.localConfiguration?.subtitleConfigurations.orEmpty()
            .filterNot { it.uri.toString() in ours }
        val rebuilt = kept + externalSubs.map { sub ->
            subtitleConfig(
                sub.current,
                sub.label + offsetLabel(sub.offsetMs),
                selected = sub == externalSubs.lastOrNull(),
            )
        }
        val item = current.buildUpon().setSubtitleConfigurations(rebuilt).build()
        player.setMediaItem(item, pos)
        player.prepare()
        player.play()
    }

    private fun offsetLabel(ms: Long): String = when {
        ms == 0L -> ""
        else -> " (${if (ms > 0) "+" else "−"}%.1f s)".format(kotlin.math.abs(ms) / 1000.0)
    }

    private fun subtitleConfig(file: String, label: String, selected: Boolean) =
        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(file))
            .setMimeType(
                if (file.endsWith(".vtt", true) || file.contains(".vtt?")) MimeTypes.TEXT_VTT
                else MimeTypes.APPLICATION_SUBRIP,
            )
            .setLabel(label)
            .setSelectionFlags(if (selected) C.SELECTION_FLAG_DEFAULT else 0)
            .build()

    /**
     * §audio (conversation 1) — **boost jusqu'à 200 %** (+6 dB) via un
     * LoudnessEnhancer attaché à la session audio d'ExoPlayer (pas de
     * ré-encodage, fonctionne sur toutes les pistes).
     */
    fun setAudioBoost(percent: Int) {
        _audioBoostPercent.value = percent.coerceIn(100, 200)
        applyAudioBoost()
    }

    private fun applyAudioBoost() {
        val session = runCatching { player.audioSessionId }.getOrDefault(C.AUDIO_SESSION_ID_UNSET)
        if (session == C.AUDIO_SESSION_ID_UNSET || session == 0) return
        runCatching {
            if (loudness == null) {
                loudness = android.media.audiofx.LoudnessEnhancer(session).apply { enabled = true }
            }
            // 200 % en amplitude = 20·log10(2) ≈ 6,02 dB = 602 mB.
            val p = _audioBoostPercent.value
            val gainMb = (2000.0 * kotlin.math.log10(p / 100.0)).toInt()
            loudness?.setTargetGain(gainMb)
        }
    }

    /** Sets extension headers (Referer/UA/cookies) on the shared OkHttpDataSource. */
    fun prepareWithHeaders(headers: Map<String, String>) {
        httpFactory.setDefaultRequestProperties(headers)
    }

    override fun play() {
        if (player.mediaItemCount == 0) return
        if (player.playbackState == Player.STATE_ENDED) {
            // A user replay starts a fresh completion generation; it may auto-advance again.
            playbackGeneration += 1
            player.seekTo(0L)
        }
        if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
        player.play()
    }
    override fun pause() = player.pause()
    private fun cancelRelativeSeek() {
        relativeSeekJob?.cancel(); relativeSeekJob = null; relativeSeekTarget.clear()
    }
    override fun seekTo(ms: Long) {
        cancelRelativeSeek()
        player.seekTo(ms.coerceAtLeast(0L))
    }
    override fun seekBy(deltaMs: Long) {
        if (!player.isCurrentMediaItemSeekable) return
        _positionMs.value = relativeSeekTarget.add(player.currentPosition, deltaMs, player.duration)
        // A bounded 60 ms batch reduces decoder churn without dropping taps or waiting for buffering.
        if (relativeSeekJob == null) relativeSeekJob = scope.launch {
            delay(60)
            val target = relativeSeekTarget.take()
            relativeSeekJob = null
            if (target != null) player.seekTo(target)
        }
    }

    /**
     * §vitesse : 0,25× → 4× avec **correction du pitch** (la voix reste naturelle
     * jusqu'à 1,5×, au-delà le son est accéléré comme sur YouTube).
     */
    override fun setSpeed(factor: Float) {
        val speed = factor.coerceIn(0.25f, 4f)
        player.playbackParameters = PlaybackParameters(speed, 1f)
    }

    /** §vitesse : vitesse temporaire (appui long) — restaurée par l'écran. */
    fun setTemporarySpeed(factor: Float) {
        player.playbackParameters = PlaybackParameters(factor.coerceIn(0.25f, 4f), 1f)
    }

    /**
     * Enables the GPU filter chain or removes it entirely for native SurfaceView
     * playback. With no frame effects, Android/Media3 can use the device HDR path
     * when the stream, decoder, display, and OS all support it.
     */
    fun setVideoEffectsEnabled(enabled: Boolean) {
        if (videoEffectsEnabled == enabled) return
        player.setVideoEffects(if (enabled) listOf(liveVideoEffect) else emptyList())
        videoEffectsEnabled = enabled
    }

    /** Updates uniforms only: no renderer rebuild, surface replacement, or buffer reallocation. */
    fun applyVideoSettings(settings: LiveVideoSettings) {
        if (!liveVideoEffect.update(settings)) return
        if (!player.playWhenReady && player.playbackState == Player.STATE_READY && player.isCurrentMediaItemSeekable) {
            player.seekTo(player.currentPosition.coerceAtLeast(0L))
        }
    }

    override fun selectSubtitle(track: SubtitleTrack?) {
        // Sidecar subtitles are attached as SubtitleConfigurations; toggling is a track override.
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, track == null)
            .build()
    }

    override fun selectAudio(trackId: String?) {
        val overrides = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .mapIndexedNotNull { _, group ->
                group.mediaTrackGroup.takeIf { g ->
                    (0 until g.length).any { i -> g.getFormat(i).id == trackId }
                }?.let { TrackSelectionOverride(it, 0) }
            }.firstOrNull()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .apply { if (overrides != null) addOverride(overrides) }
            .build()
    }

    override fun setSubtitleStyle(style: SubtitleStyle) {
        subtitleStyle = style
        // Applied by the view layer: PlayerView.setSubtitleView with CaptionStyleCompat
    }

    fun captionStyle(): CaptionStyleCompat = CaptionStyleCompat(
        subtitleStyle.foregroundColor,
        subtitleStyle.backgroundColor,
        android.graphics.Color.TRANSPARENT,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        subtitleStyle.outlineColor,
        android.graphics.Typeface.create(subtitleStyle.fontName, android.graphics.Typeface.NORMAL),
    )

    override fun release() {
        cancelRelativeSeek()
        runCatching { loudness?.release() }
        loudness = null
        player.release()
    }

    private fun publishTracks(tracks: Tracks) {
        _availableAudio.value = tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group ->
                (0 until group.mediaTrackGroup.length).map { i ->
                    val f = group.mediaTrackGroup.getFormat(i)
                    AudioTrackInfo(f.id ?: "audio-$i", f.label ?: f.language ?: "Piste ${i + 1}", f.language)
                }
            }
    }

    private fun SubtitleTrack.toConfiguration(): MediaItem.SubtitleConfiguration {
        val mime = when (format) {
            dev.endlesssea.extensions.api.model.SubtitleFormat.SRT -> MimeTypes.APPLICATION_SUBRIP
            dev.endlesssea.extensions.api.model.SubtitleFormat.VTT -> MimeTypes.TEXT_VTT
            dev.endlesssea.extensions.api.model.SubtitleFormat.ASS,
            dev.endlesssea.extensions.api.model.SubtitleFormat.SSA,
            -> MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_VTT
        }
        return MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
            .setMimeType(mime)
            .setLanguage(lang)
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }
}
