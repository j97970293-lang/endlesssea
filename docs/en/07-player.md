# 07 — Video Player

> 🇫🇷 **Résumé** : lecteur basé sur **Media3/ExoPlayer** derrière une interface `PlayerEngine` (backend remplaçable — ex. mpv-android plus tard). Lecture locale + flux des extensions, HLS/DASH/fichiers, pistes audio/sous-titres, styles de sous-titres, vitesse, gestes, verrouillage, reprise de position, PiP, verrou d'orientation. N'importe quel élément du lecteur peut être remplacé sans toucher au reste de l'app (§25).

---

## 1. Interface (contract in `:player`)

```kotlin
interface PlayerEngine {
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val availableSubtitles: StateFlow<List<SubtitleTrack>>
    val availableAudio: StateFlow<List<AudioTrackInfo>>

    fun prepare(list: List<VideoLink>, subtitles: List<SubtitleTrack>, startPositionMs: Long)
    fun play(); fun pause(); fun seekTo(ms: Long); fun seekBy(deltaMs: Long)
    fun setSpeed(factor: Float)                       // 0.25×–3×, spec §7
    fun selectSubtitle(track: SubtitleTrack?); fun selectAudio(trackId: String?)
    fun setSubtitleStyle(style: SubtitleStyle)        // size/font/color/outline, spec §7
    fun release()
}
```

`EsPlayer` = Media3 implementation. Alternative backends (mpv via `aniyomiorg`-style binding, VLC/LibVLC) implement the same interface and are chosen in Settings.

## 2. Feature coverage (spec §7)

| Requirement | Implementation |
|---|---|
| Local files | `MediaItem.fromUri(file/saf)`; SAF URIs persisted with takeable permissions (doc 08) |
| HLS / DASH / progressive | Media3 `DefaultMediaSourceFactory` (+ okhttp datasource with extension headers) |
| Fullscreen & auto-rotate | Sensor-driven orientation + lock toggle |
| Playback speed | `setSpeed`, persisted per-profile |
| Audio & subtitle tracks | `Tracks` API; external sidecars attached as `SubtitleConfiguration` |
| Subtitle styling | `CaptionStyleCompat` wrapper (`SubtitleStyle`) incl. size, font, color, outline, background — ASS/SSA rendered faithfully if parsed to media3 cues; libass backend on roadmap for 100 % fidelity (Nuvio lesson) |
| Subtitle sync offset | ±ms slider, applied to external tracks |
| Seek/ff | Double-tap ±10 s zones, drag-to-seek with thumbnail rail |
| Auto-resume | Position persisted every 5 s + on pause → `HistoryRepository`; "resume from 12:34" chip |
| Screen lock | Overlay lock (blocks gestures), long-press unlock |
| Gestures | Left/bottom brightness, right volume, pinch zoom-fit/crop |
| Lockscreen / background | Media3 `MediaSessionService` for local playback in background |
| PiP | API 26+ `enterPictureInPictureMode` |
| TV | Leanback-friendly focusable controls (phase 2, spec §24) |

## 3. Headers & cookies

Extension-supplied `VideoLink.headers` (Referer/User-Agent/cookies post-CAPTCHA) are injected via Media3 `OkHttpDataSource.Factory` configured with the extension's cookie jar — no separate network stack for playback.

## 4. Errors surfaced to the user

Typed mapping: `VideoUnavailable`, `NetworkError` (with retry), `SourceUnavailable`, `CaptchaRequired` (bubbles back to the verification screen then resumes). "Next server" is offered when auto-server rules exist (spec §14).

## 5. Performance notes

- `ExoPlayer` created per playback activity, released on destroy; default buffer tuned for mobile (`DefaultLoadControl` 25 MB cap on ≤2 GB RAM devices).
- TextureView vs SurfaceView auto (SurfaceView default; TextureView when animated transitions needed).
- Hardware decoding left to the platform (no forced software disable) + codec fallback logging for diagnostics.