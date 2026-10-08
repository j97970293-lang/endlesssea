# 06 — Download System

> 🇫🇷 **Résumé** : moteur de téléchargement maison inspiré du blueprint d'AB Download Manager (Apache-2.0) : sonde HEAD → plan de segments → connexions HTTP `Range` parallèles → écriture positionnée dans un unique fichier `.part` → vérification → renommage final + sous-titres séparés. File d'attente avec priorités, pause/reprise, reprise après crash/redémarrage, service de premier plan + notifications.

---

## 1. Requirements recap (spec §5, §6)

Parallel segmented downloads · resume after interruption/crash/reboot · pause/resume · background queue · N simultaneous tasks · server & quality choice at enqueue · subtitle sidecar files (SRT/ASS/VTT) · direct files + HLS (DASH download is not implemented) · integrity check · progress notifications · clear errors.

## 2. Components

```
:downloader
├── DownloadManager      queue state machine, priorities, concurrency (default: 2 active, Wi-Fi rules)
├── SegmentEngine        pure-Kotlin engine (unit-testable with MockWebServer)
│   ├── probe()          HEAD/GET-Range:0-0 → size, acceptRanges, etag, filename
│   ├── plan()           segment table (adaptive 1–8 parts)
│   └── run()            Range workers + positioned writes + checkpointing
├── HlsEngine            Media3 HLS playlist parser → variants → sequential .ts parts + concatenation
├── DownloadService      foregroundServiceType="dataSync", wake lock, notification channels
└── NotificationHelper   per-task progress notif (setOngoing), done/error notifs
```

## 3. Lifecycle state machine

`QUEUED → PROBING → DOWNLOADING ⇄ PAUSED → MERGING/VERIFYING → COMPLETED`
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;any state `→ FAILED(reason)` (retryable) · `CANCELLED` removes artifacts.

Persisted in Room (`download_tasks`, `download_segments`) on every checkpoint → the app may be killed at any moment; on boot the manager restores `DOWNLOADING/PAUSED` tasks and recomputes progress **from real on-disk bytes** (size check vs segment table), à la ABDM "honest resume".

## 4. Segment planning (direct files)

```
probe(url, headers):
  HEAD → 200?  → length, Accept-Ranges: bytes?
         405?  → GET with Range: bytes=0-0 → Content-Range
plan(length, rangesOK):
  if !rangesOK or length < 8 MiB  → 1 segment (single connection)
  else parts = clamp(length / 8 MiB, 2, 8)  // IDM-like, bounded for mobile
      segment[i] = [i*len/parts, min((i+1)*len/parts-1, len-1)]
run():
  open "<name>.part" with positioned writes (FileChannel/SeekableByteChannel)
  worker i: GET Range: start+downloaded..end → write at offset → checkpoint every 256 KiB / 1 s
```

Key decisions:
- **Single `.part` file with positioned writes** (ABDM `SegmentedDownloadDestination` idea) → no merge step, pause = cancel coroutines only.
- Fallback for FAT/exFAT SD targets (no sparse files, slow random I/O): per-part files + concat at the end (doc 08).
- **Adaptive throttling**: on HTTP 429/503 the active segment count steps down; retries with exponential backoff + jitter; `Retry-After` honored.
- **Integrity**: total-size verification mandatory; optional server-provided `ETag`/`Content-MD5`/extension-provided SHA-256 check.
- Stalled workers (no bytes for 20 s) are recycled; a temp redirect resolution refreshes expiring signed URLs on resume (extension may supply a `refreshUrl` callback through `VideoLink.headers["X-ES-Refresh"]`).

## 5. HLS (and DASH note)

1. `HlsEngine` parses master/media playlists and resolves relative URLs.
2. An explicit quality selects a matching `RESOLUTION` height; if unavailable/unidentifiable, the task fails rather than silently upgrading. Only UNKNOWN/automatic quality selects the highest advertised `BANDWIDTH`. Redirected and nested playlists are resolved with a four-manifest limit and a 2 MiB bound per manifest.
3. Segments are fetched sequentially and assembled into a `.ts` or fMP4 output (`EXT-X-MAP`). New HLS checkpoints store the cumulative committed file offset; a matching plan fingerprint is required for resume. Byte-range playlists/maps are explicitly rejected instead of repeatedly downloading whole resources. See [size and resume limitations](../maintenance/download-sizes.md).
4. AES-128-CBC is supported. SAMPLE-AES and unsupported encryption methods are rejected; this is not a DRM-download implementation.
5. DASH playback is supported by Media3, but `DownloadManager.runTask` explicitly rejects DASH tasks with “lecture en ligne uniquement pour l'instant”. Direct `BaseURL` extraction/muxing is **not implemented**.

These implementation notes supersede the earlier “DASH best-effort” design. Playback/download compatibility still requires real-source and device testing.

## 6. Subtitles (spec §6)

- Downloaded next to the video: `Movie Title (1982).mp4` + `Movie Title (1982).fr.srt` (+ `.ass`/`.vtt`).
- User picks per-task: video only / video + selected subs / "all subs".
- Optional post-step "mux when possible" (MKV with embedded subs) is **opt-in** and uses a bundled lightweight muxer later; default = separated files (spec §6 explicit wish).

## 7. Queue & concurrency policy

- Defaults: 2 parallel tasks, 4 segments each (configurable 1–5 tasks, 1–8 segments).
- Wi-Fi-only toggle; unmetered-when-charging "boost" toggle; global speed limit (KiB/s) with live change.
- Priorities: manual reorder in Downloads screen; "Download whole season" enqueues a batch with stable ordering.
- Duplicates: task dedup by `(mediaId, episodeId, quality, server)`; existing files detected → "already downloaded" (spec §24).
- Auto-retry policy: 3 attempts, backoff 30 s → 2 min → 10 min; unrecoverable errors surface typed reasons (`DownloadInterrupted`, `Server unavailable`, `Write failed`…).

## 8. Android integration

- **Foreground service** `dataSync`, started on first active task, self-stops when idle 2 min; notification channel `downloads` (low importance, ongoing + actions Pause/Cancel).
- WakeLock `PARTIAL_WAKE_LOCK` held while segments are active only; releases on pause.
- Back-up across reboots: `BOOT_COMPLETED` optional rescheduling (off by default, privacy).
- Battery: progress notifications throttled to 1 Hz; Room checkpoints batched.

## 9. One-click UX (spec §5)

```
Sheet "Télécharger"  (from media page or player)
├─ Server:   [ Serveur 1 ▼ ]  (auto = first working per rules)
├─ Quality:  ( ) 360p ( ) 480p (•) 720p ( ) 1080p
├─ Subs:     [x] FR  [ ] EN  …
└─ [ Télécharger ]  → enqueue + snackbar "Ajouté à la file"
```

"Download season" = the same sheet applied to a selection of episodes.

## 10. Mapping to ABDM blueprint (credit)

Concepts adapted: `HttpDownloaderClient`-style probe, segmented destination with positioned writes, part-state persistence, queue with priority/concurrency, speed limiting, HLS part strategy. Implementation is original Kotlin for Android (`:downloader`); ABDM is Apache-2.0 and credited in `NOTICE`.
