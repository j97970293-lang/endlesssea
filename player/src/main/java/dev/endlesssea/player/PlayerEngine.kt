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

data class SubtitleStyle(
    val textScale: Float = 1.0f,
    val fontName: String = "sans-serif",
    val foregroundColor: Int = 0xFFFFFFFF.toInt(),
    val outlineColor: Int = 0xFF000000.toInt(),
    val backgroundColor: Int = 0x80000000.toInt(),
)
