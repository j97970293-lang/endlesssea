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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _isPlaying = MutableStateFlow(false)
    private val _positionMs = MutableStateFlow(0L)
    private val _durationMs = MutableStateFlow(0L)
    private val _availableSubtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    private val _availableAudio = MutableStateFlow<List<AudioTrackInfo>>(emptyList())

    override val isPlaying: StateFlow<Boolean> = _isPlaying
    override val positionMs: StateFlow<Long> = _positionMs
    override val durationMs: StateFlow<Long> = _durationMs
    override val availableSubtitles: StateFlow<List<SubtitleTrack>> = _availableSubtitles
    override val availableAudio: StateFlow<List<AudioTrackInfo>> = _availableAudio

    var subtitleStyle: SubtitleStyle = SubtitleStyle()
        private set

    private var currentIndex = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { _isPlaying.value = playing }
            override fun onTracksChanged(tracks: Tracks) { publishTracks(tracks) }
        })
        scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (player.isPlaying) {
                    _positionMs.value = player.currentPosition
                    _durationMs.value = player.duration.coerceAtLeast(0)
                }
                delay(1_000)
            }
        }
    }

    override fun prepare(links: List<VideoLink>, startPositionMs: Long) {
        // Extension headers (Referer/UA/post-CAPTCHA cookies) apply to every item.
        prepareWithHeaders(links.firstOrNull()?.headers ?: emptyMap())

        val items = links.map { link ->
            MediaItem.Builder()
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
        }
        player.setMediaItems(items, currentIndex, startPositionMs)
        _availableSubtitles.value = links.getOrNull(currentIndex)?.subtitles ?: emptyList()
        player.prepare()
    }

    /** Sets extension headers (Referer/UA/cookies) on the shared OkHttpDataSource. */
    fun prepareWithHeaders(headers: Map<String, String>) {
        httpFactory.setDefaultRequestProperties(headers)
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun seekBy(deltaMs: Long) = player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))

    override fun setSpeed(factor: Float) {
        player.playbackParameters = PlaybackParameters(factor.coerceIn(0.25f, 3f))
    }

    /**
     * Filtres vidéo temps réel (luminosité/teinte/saturation via HslAdjustment composé
     * par l'app) — la surface du player DOIT être une TextureView pour être visible.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun applyVideoEffects(effects: List<androidx.media3.common.Effect>) {
        runCatching { player.setVideoEffects(effects) }
        // §filtres-video : ExoPlayer ne réinjecte la chaîne d'effets qu'à la frame
        // suivante — en pause (ou sur certains décodeurs) l'image restait inchangée
        // jusqu'à ce qu'on quitte la vidéo. Un micro-seek force le re-rendu.
        runCatching {
            val pos = player.currentPosition
            if (player.duration > 0 || pos > 0) player.seekTo(pos)
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
