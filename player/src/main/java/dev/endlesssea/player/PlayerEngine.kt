package dev.endlesssea.player

import dev.endlesssea.extensions.api.model.SubtitleTrack
import dev.endlesssea.extensions.api.model.VideoLink
import kotlinx.coroutines.flow.StateFlow

/** Replaceable playback contract (docs/en/07 §1). Media3 impl: [EsPlayer]. */
interface PlayerEngine {
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val availableSubtitles: StateFlow<List<SubtitleTrack>>
    val availableAudio: StateFlow<List<AudioTrackInfo>>

    fun prepare(links: List<VideoLink>, startPositionMs: Long = 0)
    fun play()
    fun pause()
    fun seekTo(ms: Long)
    fun seekBy(deltaMs: Long)
    fun setSpeed(factor: Float)
    fun selectSubtitle(track: SubtitleTrack?)
    fun selectAudio(trackId: String?)
    fun setSubtitleStyle(style: SubtitleStyle)
    fun release()
}

data class AudioTrackInfo(val id: String, val label: String, val language: String?)

/** §stats — surimpression technique demandée par la conv. 1 (FPS, débit, résolution). */
data class PlayerStats(
    val width: Int = 0,
    val height: Int = 0,
    val pixelRatio: Float = 1f,
    val bitrate: Int = 0,
    val codec: String = "",
    val frameRate: Float = 0f,
    val droppedFrames: Int = 0,
    val bufferedMs: Long = 0L,
    val speed: Float = 1f,
    val playbackState: Int = 0,
) {
    val resolution: String
        get() = if (width > 0 && height > 0) "${width}×${height}" else "—"

    val bitrateLabel: String
        get() = if (bitrate > 0) "%.2f Mb/s".format(bitrate / 1_000_000.0) else "—"

    val fpsLabel: String get() = if (frameRate > 0f) "%.2f im/s".format(frameRate) else "—"

    val bufferedLabel: String
        get() = "%d s".format((bufferedMs / 1000).coerceAtLeast(0))
}

data class SubtitleStyle(
    val textScale: Float = 1.0f,
    val fontName: String = "sans-serif",
    val foregroundColor: Int = 0xFFFFFFFF.toInt(),
    val outlineColor: Int = 0xFF000000.toInt(),
    val backgroundColor: Int = 0x80000000.toInt(),
)
